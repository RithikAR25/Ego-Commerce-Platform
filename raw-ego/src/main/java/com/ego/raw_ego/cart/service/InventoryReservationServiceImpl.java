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
 * Implementation of {@link InventoryReservationService} — manages the inventory reservation
 * lifecycle for cart operations with optimistic lock retry.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryReservationServiceImpl implements InventoryReservationService {

    private static final int MAX_RETRIES = 3;

    private final InventoryRecordRepository inventoryRepository;

    @Override
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

    @Override
    @Transactional
    public void release(Long variantId, int delta) {
        InventoryRecord record = findRecord(variantId);

        int actualDelta = Math.min(delta, record.getQuantityReserved());
        if (actualDelta <= 0) {
            log.warn("Release skipped — nothing reserved for variantId={}", variantId);
            return;
        }

        adjustReservedWithRetry(record, -actualDelta, "release");
        log.info("Inventory released: variantId={} delta=-{}", variantId, actualDelta);
    }

    @Override
    @Transactional
    public void commit(Long variantId, int delta) {
        InventoryRecord record = findRecord(variantId);

        if (record.getQuantityAvailable() < delta) {
            throw new ConflictException(
                    "Insufficient stock to commit purchase for variant id=" + variantId +
                    ". Requested: " + delta + ", Available: " + record.getQuantityAvailable());
        }

        int rowsUpdated = inventoryRepository.adjustQuantityWithOptimisticLock(
                record.getId(), -delta, record.getVersion());

        if (rowsUpdated == 0) {
            throw new ConflictException(
                    "Stock commit failed due to concurrent modification for variant id=" + variantId +
                    ". Please retry.");
        }

        InventoryRecord freshRecord = findRecord(variantId);
        if (freshRecord.getQuantityReserved() >= delta) {
            release(variantId, delta);
        } else {
            log.warn("Skipping reserved decrement on commit: variantId={} requested={} reserved={}",
                    variantId, delta, freshRecord.getQuantityReserved());
        }

        log.info("Inventory committed: variantId={} units={}", variantId, delta);
    }

    @Override
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

    private void adjustReservedWithRetry(InventoryRecord record, int delta, String operation) {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            if (attempts > 0) {
                record = findRecord(record.getVariant().getId());
            }

            int rowsUpdated = inventoryRepository.adjustReservedWithOptimisticLock(
                    record.getId(), delta, record.getVersion());

            if (rowsUpdated == 1) return;

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
