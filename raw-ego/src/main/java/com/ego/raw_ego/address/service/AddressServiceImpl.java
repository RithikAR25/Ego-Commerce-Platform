package com.ego.raw_ego.address.service;

import com.ego.raw_ego.address.dto.AddressRequest;
import com.ego.raw_ego.address.dto.AddressResponse;
import com.ego.raw_ego.address.entity.UserAddress;
import com.ego.raw_ego.address.repository.UserAddressRepository;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link AddressService} — business logic for the user address book feature.
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
@Service
@Slf4j
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private static final int MAX_ADDRESSES = 5;

    private final UserAddressRepository addressRepository;

    // ── List ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(Long userId) {
        return addressRepository.findActiveByUserId(userId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddress(Long userId, Long addressId) {
        return AddressResponse.from(findOwned(userId, addressId));
    }

    // ── Create ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest request) {
        long activeCount = addressRepository.countByUserIdAndIsActiveTrue(userId);
        if (activeCount >= MAX_ADDRESSES) {
            throw new ConflictException(
                    "Address book is full. You can save a maximum of " + MAX_ADDRESSES + " addresses. " +
                    "Please delete an existing address before adding a new one.");
        }

        // First address is automatically set as default
        boolean noExistingAddresses = activeCount == 0;
        boolean shouldBeDefault = noExistingAddresses || request.isSetAsDefault();

        if (shouldBeDefault && !noExistingAddresses) {
            // Clear existing defaults before assigning new one
            addressRepository.clearDefaultForUser(userId);
        }

        UserAddress address = buildAddress(userId, request, shouldBeDefault);
        address = addressRepository.save(address);

        log.info("[Address] Added: userId={} addressId={} default={}", userId, address.getId(), shouldBeDefault);
        return AddressResponse.from(address);
    }

    // ── Update ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        UserAddress address = findOwned(userId, addressId);

        address.setFullName(HtmlSanitizer.sanitize(request.getFullName().trim(), 100));
        address.setPhone(HtmlSanitizer.sanitize(request.getPhone().trim(), 20));
        address.setAddressLine1(HtmlSanitizer.sanitize(request.getAddressLine1().trim(), 255));
        address.setAddressLine2(request.getAddressLine2() != null
                ? HtmlSanitizer.sanitize(request.getAddressLine2().trim(), 255) : null);
        address.setLandmark(request.getLandmark() != null
                ? HtmlSanitizer.sanitize(request.getLandmark().trim(), 255) : null);
        address.setCity(HtmlSanitizer.sanitize(request.getCity().trim(), 100));
        address.setState(HtmlSanitizer.sanitize(request.getState().trim(), 100));
        address.setPinCode(HtmlSanitizer.sanitize(request.getPinCode().trim(), 20));
        address.setCountry(request.getCountry() != null
                ? HtmlSanitizer.sanitize(request.getCountry().trim(), 100) : "India");
        if (request.getAddressType() != null) {
            address.setAddressType(request.getAddressType());
        }

        if (request.isSetAsDefault() && !address.isDefault()) {
            addressRepository.clearDefaultForUser(userId);
            address.setDefault(true);
        }

        address = addressRepository.save(address);
        log.info("[Address] Updated: userId={} addressId={}", userId, addressId);
        return AddressResponse.from(address);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        UserAddress address = findOwned(userId, addressId);

        if (address.isDefault()) {
            throw new ConflictException(
                "Cannot delete your default address. Please set another address as default first.");
        }

        address.setActive(false);
        addressRepository.save(address);
        log.info("[Address] Soft-deleted: userId={} addressId={}", userId, addressId);
    }

    // ── Set Default ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {
        UserAddress address = findOwned(userId, addressId);

        addressRepository.clearDefaultForUser(userId);
        address.setDefault(true);
        address = addressRepository.save(address);

        log.info("[Address] Default set: userId={} addressId={}", userId, addressId);
        return AddressResponse.from(address);
    }

    // ── Checkout helper ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserAddress getForCheckout(Long userId, Long addressId) {
        return findOwned(userId, addressId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAddress getDefaultAddress(Long userId) {
        return addressRepository.findByUserIdAndIsDefaultTrueAndIsActiveTrue(userId).orElse(null);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private UserAddress findOwned(Long userId, Long addressId) {
        return addressRepository.findByIdAndUserIdAndIsActiveTrue(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Address not found: id=" + addressId));
    }

    private UserAddress buildAddress(Long userId, AddressRequest request, boolean isDefault) {
        return UserAddress.builder()
                .userId(userId)
                .fullName(HtmlSanitizer.sanitize(request.getFullName().trim(), 100))
                .phone(HtmlSanitizer.sanitize(request.getPhone().trim(), 20))
                .addressLine1(HtmlSanitizer.sanitize(request.getAddressLine1().trim(), 255))
                .addressLine2(request.getAddressLine2() != null
                        ? HtmlSanitizer.sanitize(request.getAddressLine2().trim(), 255) : null)
                .landmark(request.getLandmark() != null
                        ? HtmlSanitizer.sanitize(request.getLandmark().trim(), 255) : null)
                .city(HtmlSanitizer.sanitize(request.getCity().trim(), 100))
                .state(HtmlSanitizer.sanitize(request.getState().trim(), 100))
                .pinCode(HtmlSanitizer.sanitize(request.getPinCode().trim(), 20))
                .country(request.getCountry() != null
                        ? HtmlSanitizer.sanitize(request.getCountry().trim(), 100) : "India")
                .addressType(request.getAddressType() != null
                        ? request.getAddressType()
                        : UserAddress.AddressType.HOME)
                .isDefault(isDefault)
                .build();
    }
}
