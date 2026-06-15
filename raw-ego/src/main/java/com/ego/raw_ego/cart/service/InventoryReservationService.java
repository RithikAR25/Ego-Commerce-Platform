package com.ego.raw_ego.cart.service;

/**
 * Contract for inventory reservation lifecycle management for cart operations.
 *
 * <p><b>Reservation lifecycle (LOCKED):</b>
 * <pre>
 *   ADD TO CART       → reserve(variantId, +qty)    → quantity_reserved += qty
 *   UPDATE CART UP    → reserve(variantId, +delta)
 *   UPDATE CART DOWN  → release(variantId, +delta)  → quantity_reserved -= delta
 *   REMOVE FROM CART  → release(variantId, +qty)
 *   PLACE ORDER       → commit(variantId, qty)       → quantity_available -= qty, quantity_reserved -= qty
 *   CANCEL ORDER      → restore(variantId, qty)      → quantity_available += qty
 *   REDIS TTL EXPIRY  → release(variantId, qty)      → quantity_reserved -= qty
 * </pre>
 *
 * <p><b>Concurrency protection:</b>
 * All mutations use optimistic locking with a retry loop (max 3 attempts).
 * On persistent conflict, a {@link com.ego.raw_ego.common.exception.ConflictException} (HTTP 409) is thrown.
 */
public interface InventoryReservationService {

    /**
     * Increments {@code quantity_reserved} by {@code delta} for the given variant.
     * Validates that sufficient stock exists before reserving.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if stock is insufficient or retries exhausted
     */
    void reserve(Long variantId, int delta);

    /**
     * Decrements {@code quantity_reserved} by {@code delta}.
     * Silently caps the decrement at the current reserved amount.
     */
    void release(Long variantId, int delta);

    /**
     * Atomically commits a purchase: decrements both {@code quantity_available}
     * and {@code quantity_reserved} by {@code delta}.
     * Called at order placement.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if stock is insufficient
     */
    void commit(Long variantId, int delta);

    /**
     * Restores {@code quantity_available} by {@code delta} — the inverse of {@link #commit}.
     * Called on order cancellation or return approval.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if all retry attempts fail
     */
    void restore(Long variantId, int delta);
}
