package com.ego.raw_ego.returns.service;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.returns.dto.request.AdminReviewReturnRequest;
import com.ego.raw_ego.returns.dto.request.InitiateReturnRequest;
import com.ego.raw_ego.returns.dto.response.ReturnRequestResponse;
import com.ego.raw_ego.returns.enums.ReturnStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for the Return &amp; Refund module.
 *
 * <h3>Return flow</h3>
 * <ol>
 *   <li>Customer calls {@code POST /api/v1/orders/{orderId}/returns} with reason.</li>
 *   <li>{@link #initiateReturn} validates: order DELIVERED, within 7-day window, no active return.</li>
 *   <li>Admin calls {@code PUT /api/v1/admin/returns/{returnId}/review}.</li>
 *   <li>{@link #adminReviewReturn} handles approve/reject:
 *     <ul>
 *       <li>Reject: status → REJECTED, order unchanged.</li>
 *       <li>Approve: Razorpay refund API called (OUTSIDE @Transactional), then
 *           status → REFUND_COMPLETED, order → REFUNDED, inventory restored.</li>
 *     </ul>
 *   </li>
 * </ol>
 */
public interface ReturnService {

    /**
     * Customer submits a return request for a delivered order.
     *
     * @throws com.ego.raw_ego.common.exception.ConflictException if guards fail
     */
    ReturnRequestResponse initiateReturn(User user, Long orderId, InitiateReturnRequest request);

    /**
     * Customer retrieves the status of their return request for a given order.
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if no return exists
     */
    ReturnRequestResponse getOrderReturn(Long orderId, Long userId);

    /** Admin: list all return requests with optional status filter. */
    Page<ReturnRequestResponse> adminGetReturns(ReturnStatus status, Pageable pageable);

    /**
     * Admin: retrieves a specific return request by ID.
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if not found
     */
    ReturnRequestResponse adminGetReturn(Long returnId);

    /**
     * Admin: approve or reject a return request.
     * Approve triggers a Razorpay refund (OUTSIDE @Transactional) and restores inventory.
     */
    ReturnRequestResponse adminReviewReturn(Long returnId, AdminReviewReturnRequest request);
}
