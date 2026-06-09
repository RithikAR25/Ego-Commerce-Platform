package com.ego.raw_ego.address.controller;

import com.ego.raw_ego.address.dto.AddressRequest;
import com.ego.raw_ego.address.dto.AddressResponse;
import com.ego.raw_ego.address.service.AddressService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the user address book.
 *
 * <pre>
 * POST   /api/v1/addresses              → add a new address           [JWT]
 * GET    /api/v1/addresses              → list all active addresses   [JWT]
 * GET    /api/v1/addresses/{id}         → get a single address        [JWT]
 * PUT    /api/v1/addresses/{id}         → full update                 [JWT]
 * DELETE /api/v1/addresses/{id}         → soft-delete                 [JWT]
 * PATCH  /api/v1/addresses/{id}/default → set as default              [JWT]
 * </pre>
 *
 * <p>All endpoints infer the user ID from the validated JWT. No userId in the path.
 */
@RestController
@RequestMapping("/api/v1/addresses")
@Tag(name = "Address Book", description = "Manage saved shipping addresses (max 5 per user)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    @Operation(summary = "Add a new address",
               description = "Creates a new address in the user's address book. Returns 409 if 5 addresses already exist.")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = extractUserId(userDetails);
        AddressResponse address = addressService.addAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address added successfully.", address));
    }

    @GetMapping
    @Operation(summary = "List all saved addresses",
               description = "Returns all active addresses for the authenticated user, default-first.")
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listAddresses(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(addressService.listAddresses(userId)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single address by ID")
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(addressService.getAddress(userId, id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an address (full replace)",
               description = "Replaces all fields of the address. Returns 404 if address does not belong to user.")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                "Address updated successfully.",
                addressService.updateAddress(userId, id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an address (soft-delete)",
               description = "Marks the address as inactive. It will no longer appear in the address book " +
                             "or be selectable at checkout. The row is retained for order history integrity.")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        Long userId = extractUserId(userDetails);
        addressService.deleteAddress(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully."));
    }

    @PatchMapping("/{id}/default")
    @Operation(summary = "Set an address as the default",
               description = "Clears the default flag on all other addresses and sets this one as the default. " +
                             "The default address is pre-selected at checkout.")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefault(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id
    ) {
        Long userId = extractUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                "Default address updated.", addressService.setDefault(userId, id)));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    /**
     * Extracts the numeric userId from the UserDetails username.
     * The username is the user's email — we need to look up the User to get the ID.
     * To avoid a DB call, the UserDetails implementation must provide the user ID.
     *
     * <p>Note: Spring Security's {@code UserDetails.getUsername()} returns the email.
     * We resolve the numeric ID via the UserRepository — one DB call per request.
     * This is acceptable because address operations are low-frequency.
     */
    private Long extractUserId(UserDetails userDetails) {
        // UserDetails.getUsername() = email. The UserLoadingService will need to be injected here.
        // For now, we delegate to the AddressService to look up by email.
        // This is resolved via the overloaded method below.
        return resolveUserId(userDetails.getUsername());
    }

    private final com.ego.raw_ego.auth.repository.UserRepository userRepository;

    private Long resolveUserId(String email) {
        return userRepository.findByEmailAndDeletedFalse(email)
                .map(u -> u.getId())
                .orElseThrow(() -> new com.ego.raw_ego.common.exception.ResourceNotFoundException(
                        "Authenticated user not found: " + email));
    }
}
