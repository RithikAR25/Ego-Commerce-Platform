package com.ego.raw_ego.address.service;

import com.ego.raw_ego.address.dto.AddressRequest;
import com.ego.raw_ego.address.dto.AddressResponse;
import com.ego.raw_ego.address.entity.UserAddress;

import java.util.List;

/**
 * Contract for the user address book feature.
 *
 * <p>Business rules enforced:
 * <ul>
 *   <li>Maximum 5 active addresses per user — 6th add returns 409.</li>
 *   <li>Only one default per user — {@link #setDefault} clears all others atomically.</li>
 *   <li>Default address cannot be deleted — user must promote another address first (409).</li>
 *   <li>Delete is soft — sets {@code isActive=false} on non-default addresses.</li>
 *   <li>All mutations are ownership-scoped: {@code id + userId} must match.</li>
 * </ul>
 */
public interface AddressService {

    /** Returns all active addresses for the authenticated user, default-first. */
    List<AddressResponse> listAddresses(Long userId);

    /** Returns a single address by ID, scoped to the authenticated user. */
    AddressResponse getAddress(Long userId, Long addressId);

    /**
     * Adds a new address to the user's address book.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if the user already has 5 active addresses
     */
    AddressResponse addAddress(Long userId, AddressRequest request);

    /**
     * Replaces all mutable fields of an existing address (full update).
     * Ownership is validated — returns 404 if address does not belong to user.
     */
    AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request);

    /**
     * Soft-deletes the address (sets {@code isActive=false}).
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if the address is the current default
     */
    void deleteAddress(Long userId, Long addressId);

    /**
     * Sets the specified address as the user's default and clears all other defaults.
     * Runs in a single transaction for atomicity.
     */
    AddressResponse setDefault(Long userId, Long addressId);

    /**
     * Returns the full address entity for checkout snapshot capture.
     * Used by {@code OrderService} during checkout.
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if address does not exist or belong to user
     */
    UserAddress getForCheckout(Long userId, Long addressId);

    /** Returns the default address for checkout pre-selection. May return null. */
    UserAddress getDefaultAddress(Long userId);
}
