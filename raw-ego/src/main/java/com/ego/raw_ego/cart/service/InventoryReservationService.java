package com.ego.raw_ego.cart.service;

import com.ego.raw_ego.catalog.entity.InventoryRecord;
import com.ego.raw_ego.catalog.repository.InventoryRecordRepository;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the inventory reservation lifecycle for cart operations.
 *
 * <p><b>Reservation lifecycle (LOCKED):</b>
 * <pre>
 *   ADD TO CART       → reserve(variantId, +qty)   → quantity_reserved += qty
 *   UPDATE CART UP    → reserve(variantId, +delta)
 *   UPDATE CART DOWN  → release(variantId, +delta)  → quantity_reserved -= delta
 *   REMOVE FROM CART  → release(variantId, +qty)
 *   PLACE ORDER       → commit(variantId, qty)       → quantity_available -= qty, quantity_reserved -= qty
 *   CANCEL ORDER      → (handled in Phase 6)         → quantity_available += qty
 *   REDIS TTL EXPIRY  → release(variantId, qty)      → quantity_reserved -= qty  (Phase 5 cleanup job)
 * </pre>
 *
 * <p><b>Concurrency protection:</b>
 * All mutations use the pre-existing {@code adjustReservedWithOptimisticLock} query with a
 * retry loop (max 3 attempts) to handle JPA version conflicts gracefully.
 * On persistent conflict (flash sale load), a {@link ConflictException} (HTTP 409) is thrown.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryReservationService {

    /** Maximum number of optimistic lock retry attempts before failing with 409. */
    private static final int MAX_RETRIES = 3;

    private final InventoryRecordRepository inventoryRepository;

    // ── Public operations ─────────────────────────────────────────────────────

    /**
     * Increments {@code quantity_reserved} by {@code delta} for the given variant.
     *
     * <p>Validates that sufficient stock exists before reserving:
     * {@code quantityAvailable >= delta} (ignoring already-reserved units for UX simplicity —
     * oversell prevention is enforced at commit time via optimistic locking).
     *
     * @param variantId the variant to reserve stock for
     * @param delta     positive number of units to reserve
     * @throws ConflictException if stock is insufficient or all retry attempts fail
     */
    @Transactional
    public void reserve(Long variantId, int delta) {
        InventoryRecord record = findRecord(variantId);

        if (record.getQuantityAvailable() < delta) {
            throw new ConflictException(
                    "Insufficient stock for variant id=" + variantId +
                    ". Requested: " + delta + ", Available: " + record.getQuantityAvailable());
        }

        adjustReservedWithRetry(record, delta, "reserve");
        log.info("Inventory reserved: variantId={} delta=+{}", variantId, delta);
    }

    /**
     * Decrements {@code quantity_reserved} by {@code delta}.
     * Called when items are removed from cart or when the cart TTL expires.
     *
     * <p>Silently caps the decrement at the current reserved amount
     * to prevent negative quantities from a stale Redis state.
     *
     * @param variantId the variant to release held stock for
     * @param delta     positive number of units to release
     */
    @Transactional
    public void release(Long variantId, int delta) {
        InventoryRecord record = findRecord(variantId);

        // Cap delta to what is actually reserved — prevents going negative on stale state
        int actualDelta = Math.min(delta, record.getQuantityReserved());
        if (actualDelta <= 0) {
            log.warn("Release skipped — nothing reserved for variantId={}", variantId);
            return;
        }

        adjustReservedWithRetry(record, -actualDelta, "release");
        log.info("Inventory released: variantId={} delta=-{}", variantId, actualDelta);
    }

    /**
     * Atomically commits a purchase: decrements both {@code quantity_available}
     * and {@code quantity_reserved} by {@code delta}.
     *
     * <p>Called at order placement (Phase 6). After commit, the units
     * are neither available for sale nor shown as reserved.
     *
     * @param variantId the variant to commit stock for
     * @param delta     units being purchased
     * @throws ConflictException if stock is insufficient (race condition — should be rare)
     */
    @Transactional
    public void commit(Long variantId, int delta) {
        InventoryRecord record = findRecord(variantId);

        if (record.getQuantityAvailable() < delta) {
            throw new ConflictException(
                    "Insufficient stock to commit purchase for variant id=" + variantId +
                    ". Requested: " + delta + ", Available: " + record.getQuantityAvailable());
        }

        // Decrement available stock
        int rowsUpdated = inventoryRepository.adjustQuantityWithOptimisticLock(
                record.getId(), -delta, record.getVersion());

        if (rowsUpdated == 0) {
            throw new ConflictException(
                    "Stock commit failed due to concurrent modification for variant id=" + variantId +
                    ". Please retry.");
        }

        // Decrement reserved — best-effort.
        // quantity_reserved may be 0 if:
        //   (a) the Redis cart TTL expired and the cleanup job already released it, OR
        //   (b) the reserve() call was never reached (e.g. admin-injected cart items).
        // In both cases we must NOT throw — the quantity_available decrement above
        // is what actually prevents overselling. Reserved is an accounting field only.
        InventoryRecord freshRecord = findRecord(variantId);
        if (freshRecord.getQuantityReserved() >= delta) {
            release(variantId, delta);
        } else {
            log.warn("Skipping reserved decrement on commit: variantId={} requested={} reserved={}",
                    variantId, delta, freshRecord.getQuantityReserved());
        }

        log.info("Inventory committed: variantId={} units={}", variantId, delta);
    }

    /**
     * Restores {@code quantity_available} by {@code delta} — the inverse of {@link #commit}.
     *
     * <p>Called when a customer cancels a {@code PENDING_PAYMENT} order.
     * The committed units are returned to the available pool so they can be purchased again.
     *
     * <p>Uses an optimistic lock retry loop (max {@link #MAX_RETRIES} attempts).
     *
     * @param variantId the variant to restore stock for
     * @param delta     positive number of units to restore
     * @throws ConflictException if all retry attempts fail due to concurrent contention
     */
    @Transactional
    public void restore(Long variantId, int delta) {
        InventoryRecord record = findRecord(variantId);
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            if (attempts > 0) {
                record = findRecord(variantId);
            }

            int rowsUpdated = inventoryRepository.adjustQuantityWithOptimisticLock(
                    record.getId(), delta, record.getVersion());

            if (rowsUpdated == 1) {
                log.info("Inventory restored: variantId={} units=+{}", variantId, delta);
                return;
            }

            attempts++;
            log.warn("Optimistic lock conflict on restore for variantId={} (attempt {}/{})",
                    variantId, attempts, MAX_RETRIES);
        }

        throw new ConflictException(
                "Unable to restore inventory for variant id=" + variantId +
                " after " + MAX_RETRIES + " attempts. Please retry.");
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Retries the reserved-quantity adjustment up to {@link #MAX_RETRIES} times
     * to handle optimistic lock conflicts under concurrent load.
     */
    private void adjustReservedWithRetry(InventoryRecord record, int delta, String operation) {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            // Re-read the record on each retry to get the latest version
            if (attempts > 0) {
                record = findRecord(record.getVariant().getId());
            }

            int rowsUpdated = inventoryRepository.adjustReservedWithOptimisticLock(
                    record.getId(), delta, record.getVersion());

            if (rowsUpdated == 1) return; // Success

            attempts++;
            log.warn("Optimistic lock conflict on {} for record id={} (attempt {}/{})",
                    operation, record.getId(), attempts, MAX_RETRIES);
        }

        throw new ConflictException(
                "Unable to update inventory reservation after " + MAX_RETRIES +
                " attempts. Please try again.");
    }

    private InventoryRecord findRecord(Long variantId) {
        return inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory record not found for variant id=" + variantId));
    }
}
