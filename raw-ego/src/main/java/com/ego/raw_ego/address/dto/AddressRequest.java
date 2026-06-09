package com.ego.raw_ego.address.dto;

import com.ego.raw_ego.address.entity.UserAddress;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /api/v1/addresses and PUT /api/v1/addresses/{id}.
 *
 * <p>Validation rules match the spec exactly:
 * <ul>
 *   <li>{@code phone}: Indian mobile number format — starts with 6-9, 10 digits total</li>
 *   <li>{@code pinCode}: exactly 6 digits</li>
 *   <li>{@code fullName}: 2–100 chars</li>
 *   <li>{@code addressLine1}: 1–200 chars</li>
 * </ul>
 */
@Getter
@Setter
public class AddressRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2–100 characters")
    @Pattern(regexp = "^[a-zA-Z .'-]+$", message = "Full name must contain only letters, spaces, and . ' -")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Phone must be a valid 10-digit Indian mobile number starting with 6-9")
    private String phone;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 200, message = "Address line 1 must not exceed 200 characters")
    private String addressLine1;

    @Size(max = 200, message = "Address line 2 must not exceed 200 characters")
    private String addressLine2;

    @Size(max = 150, message = "Landmark must not exceed 150 characters")
    private String landmark;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;

    @NotBlank(message = "PIN code is required")
    @Pattern(regexp = "^\\d{6}$", message = "PIN code must be exactly 6 digits")
    private String pinCode;

    @Size(max = 100)
    private String country;

    private UserAddress.AddressType addressType;

    /** If true, this address will become the default (others cleared). */
    private boolean setAsDefault;
}
