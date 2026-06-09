package com.ego.raw_ego.returns.controller;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.response.ApiResponse;
import com.ego.raw_ego.returns.dto.request.AdminReviewReturnRequest;
import com.ego.raw_ego.returns.dto.request.InitiateReturnRequest;
import com.ego.raw_ego.returns.dto.response.ReturnRequestResponse;
import com.ego.raw_ego.returns.enums.ReturnStatus;
import com.ego.raw_ego.returns.service.ReturnService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the Return & Refund module (Phase 10).
 *
 * <h3>Customer endpoints</h3>
 * <pre>
 *   POST   /api/v1/orders/{orderId}/returns   — submit return request
 *   GET    /api/v1/orders/{orderId}/returns   — check return status
 * </pre>
 *
 * <h3>Admin endpoints</h3>
 * <pre>
 *   GET    /api/v1/admin/returns              — list all returns (paginated, filterable)
 *   GET    /api/v1/admin/returns/{returnId}   — get specific return detail
 *   PUT    /api/v1/admin/returns/{returnId}/review — approve or reject a return
 * </pre>
 *
 * <p>Authorization: Customer endpoints require any authenticated JWT.
 * Admin endpoints enforce {@code ROLE_ADMIN} via {@code @PreAuthorize}.
 *
 * <p>Pattern: {@code @AuthenticationPrincipal UserDetails} → email → {@code UserRepository}
 * lookup — same pattern as {@link com.ego.raw_ego.order.controller.OrderController}.
 */
@RestController
@Tag(name = "Returns", description = "Return request submission, status tracking, and admin review")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService   returnService;
    private final UserRepository  userRepository;

    // ── Customer endpoints ────────────────────────────────────────────────────

    /**
     * Customer submits a return request for a delivered order.
     *
     * <p>Guards: order must exist + belong to user, status must be DELIVERED,
     * within 7-day return window, no active return already exists.
     *
     * @param orderId  the EGO order to return
     * @param request  return reason + optional detail
     * @param principal the authenticated customer
     * @return 201 Created with the return request response
     */
    @PostMapping("/api/v1/orders/{orderId}/returns")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> initiateReturn(
            @PathVariable Long orderId,
            @Valid @RequestBody InitiateReturnRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        User user = resolveUser(principal);
        ReturnRequestResponse response = returnService.initiateReturn(user, orderId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Return request submitted successfully.", response));
    }

    /**
     * Customer retrieves the status of their return request for a specific order.
     *
     * @param orderId   the EGO order ID
     * @param principal the authenticated customer
     * @return 200 OK with the return request status
     */
    @GetMapping("/api/v1/orders/{orderId}/returns")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getOrderReturn(
            @PathVariable Long orderId,
            @AuthenticationPrincipal UserDetails principal) {

        User user = resolveUser(principal);
        ReturnRequestResponse response = returnService.getOrderReturn(orderId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Return request retrieved.", response));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    /**
     * Admin retrieves all return requests, optionally filtered by status.
     *
     * @param status optional status filter (REQUESTED, APPROVED, REJECTED, REFUND_INITIATED, REFUND_COMPLETED)
     * @param page   page number (0-indexed, default 0)
     * @param size   page size (default 20)
     * @return 200 OK with paginated return request list
     */
    @GetMapping("/api/v1/admin/returns")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<ReturnRequestResponse>>> adminGetReturns(
            @RequestParam(required = false) ReturnStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ReturnRequestResponse> returns = returnService.adminGetReturns(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Return requests retrieved.", returns));
    }

    /**
     * Admin retrieves a specific return request by ID.
     *
     * @param returnId the return request ID
     * @return 200 OK with full return request detail
     */
    @GetMapping("/api/v1/admin/returns/{returnId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> adminGetReturn(
            @PathVariable Long returnId) {

        ReturnRequestResponse response = returnService.adminGetReturn(returnId);
        return ResponseEntity.ok(ApiResponse.success("Return request retrieved.", response));
    }

    /**
     * Admin approves or rejects a return request.
     *
     * <p>On approval: Razorpay refund is initiated, order transitions to REFUNDED,
     * inventory is restored. On rejection: return status set to REJECTED, order unchanged.
     *
     * @param returnId the return request to review
     * @param request  approve/reject decision + refundAmount + optional admin notes
     * @return 200 OK with updated return request response
     */
    @PutMapping("/api/v1/admin/returns/{returnId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> adminReviewReturn(
            @PathVariable Long returnId,
            @Valid @RequestBody AdminReviewReturnRequest request) {

        ReturnRequestResponse response = returnService.adminReviewReturn(returnId, request);
        return ResponseEntity.ok(ApiResponse.success(
                Boolean.TRUE.equals(request.getApprove())
                        ? "Return approved. Razorpay refund initiated."
                        : "Return rejected.",
                response));
    }

    // ── Internal helper ───────────────────────────────────────────────────────

    /**
     * Resolves the authenticated user entity from the Spring Security principal.
     * Same pattern as {@link com.ego.raw_ego.order.controller.OrderController}.
     */
    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmailAndDeletedFalse(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user not found: " + principal.getUsername()));
    }
}
