package com.ego.raw_ego.coupon.controller;

import com.ego.raw_ego.common.response.ApiResponse;
import com.ego.raw_ego.coupon.dto.request.CreateCouponRequest;
import com.ego.raw_ego.coupon.dto.request.UpdateCouponRequest;
import com.ego.raw_ego.coupon.dto.response.CouponResponse;
import com.ego.raw_ego.coupon.dto.response.CouponValidationResponse;
import com.ego.raw_ego.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * REST controller for the coupon module.
 *
 * <pre>
 * ── Public (no auth) ──────────────────────────────────────────────────────
 * GET    /api/v1/coupons/validate?code=EGO20&subtotal=1299.00  → preview discount
 *
 * ── Admin (JWT + ROLE_ADMIN) ──────────────────────────────────────────────
 * POST   /api/v1/admin/coupons          → create coupon
 * GET    /api/v1/admin/coupons          → list all (paginated)
 * GET    /api/v1/admin/coupons/{id}     → get by ID
 * PUT    /api/v1/admin/coupons/{id}     → update
 * DELETE /api/v1/admin/coupons/{id}     → deactivate (soft delete)
 * </pre>
 *
 * <p>Coupon application at checkout is handled inside
 * {@link com.ego.raw_ego.order.controller.OrderController} —
 * the checkout request accepts an optional {@code couponCode} field.
 */
@RestController
@Tag(name = "Coupons", description = "Coupon validation (public) and admin coupon management")
@Validated
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    // ── Public: validate (preview) ────────────────────────────────────────────

    @GetMapping("/api/v1/coupons/validate")
    @Operation(
            summary     = "Validate a coupon code (preview)",
            description = "Returns the estimated discount for the given coupon code and subtotal. " +
                          "No side effects — does not increment usage count. Public endpoint."
    )
    public ResponseEntity<ApiResponse<CouponValidationResponse>> validateCoupon(
            @RequestParam @NotBlank(message = "Coupon code is required.") String code,
            @RequestParam @DecimalMin(value = "0.01", message = "Subtotal must be greater than 0.") BigDecimal subtotal
    ) {
        CouponValidationResponse response = couponService.validatePreview(code, subtotal);
        return ResponseEntity.ok(ApiResponse.success("Coupon validation result.", response));
    }

    // ── Admin: create ─────────────────────────────────────────────────────────

    @PostMapping("/api/v1/admin/coupons")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create coupon", description = "Creates a new coupon. Requires ROLE_ADMIN.")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CreateCouponRequest request
    ) {
        CouponResponse response = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Coupon created.", response));
    }

    // ── Admin: list ───────────────────────────────────────────────────────────

    @GetMapping("/api/v1/admin/coupons")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List coupons", description = "Paginated list of all coupons (active and inactive). Requires ROLE_ADMIN.")
    public ResponseEntity<ApiResponse<Page<CouponResponse>>> listCoupons(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<CouponResponse> page = couponService.adminListCoupons(pageable);
        return ResponseEntity.ok(ApiResponse.success("Coupons retrieved.", page));
    }

    // ── Admin: get by ID ──────────────────────────────────────────────────────

    @GetMapping("/api/v1/admin/coupons/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get coupon by ID", description = "Returns a single coupon. Requires ROLE_ADMIN.")
    public ResponseEntity<ApiResponse<CouponResponse>> getCoupon(@PathVariable Long id) {
        CouponResponse response = couponService.adminGetCoupon(id);
        return ResponseEntity.ok(ApiResponse.success("Coupon retrieved.", response));
    }

    // ── Admin: update ─────────────────────────────────────────────────────────

    @PutMapping("/api/v1/admin/coupons/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update coupon", description = "Updates coupon fields. Null fields are ignored. Requires ROLE_ADMIN.")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCouponRequest request
    ) {
        CouponResponse response = couponService.updateCoupon(id, request);
        return ResponseEntity.ok(ApiResponse.success("Coupon updated.", response));
    }

    // ── Admin: deactivate ─────────────────────────────────────────────────────

    @DeleteMapping("/api/v1/admin/coupons/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary     = "Deactivate coupon (soft delete)",
            description = "Sets active=false. The coupon is never hard-deleted to preserve order audit records. Requires ROLE_ADMIN."
    )
    public ResponseEntity<ApiResponse<Void>> deactivateCoupon(@PathVariable Long id) {
        couponService.deactivateCoupon(id);
        return ResponseEntity.ok(ApiResponse.success("Coupon deactivated.", null));
    }
}
