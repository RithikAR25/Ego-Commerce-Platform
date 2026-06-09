package com.ego.raw_ego.address.dto;

import com.ego.raw_ego.address.entity.UserAddress;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * API response DTO for a single user address.
 * Never exposes the userId (inferred from the authenticated session).
 */
@Getter
@Builder
public class AddressResponse {

    private Long   id;
    private String fullName;
    private String phone;
    private String addressLine1;
    private String addressLine2;
    private String landmark;
    private String city;
    private String state;
    private String pinCode;
    private String country;
    private String addressType;
    private boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;

    public static AddressResponse from(UserAddress a) {
        return AddressResponse.builder()
                .id(a.getId())
                .fullName(a.getFullName())
                .phone(a.getPhone())
                .addressLine1(a.getAddressLine1())
                .addressLine2(a.getAddressLine2())
                .landmark(a.getLandmark())
                .city(a.getCity())
                .state(a.getState())
                .pinCode(a.getPinCode())
                .country(a.getCountry())
                .addressType(a.getAddressType().name())
                .isDefault(a.isDefault())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
