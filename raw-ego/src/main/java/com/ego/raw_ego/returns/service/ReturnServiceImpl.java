package com.ego.raw_ego.returns.service;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.cart.service.InventoryReservationService;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.notification.event.RefundCompletedEvent;
import com.ego.raw_ego.order.entity.Order;
import com.ego.raw_ego.order.entity.OrderItem;
import com.ego.raw_ego.order.entity.OrderStatusHistory;
import com.ego.raw_ego.order.enums.OrderStatus;
import com.ego.raw_ego.order.repository.OrderRepository;
import com.ego.raw_ego.returns.dto.request.AdminReviewReturnRequest;
import com.ego.raw_ego.returns.dto.request.InitiateReturnRequest;
import com.ego.raw_ego.returns.dto.response.ReturnRequestResponse;
import com.ego.raw_ego.returns.entity.ReturnRequest;
import com.ego.raw_ego.returns.enums.ReturnStatus;
import com.ego.raw_ego.returns.repository.ReturnRequestRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Core business logic for the Return & Refund module (Phase 10).
 *
 * <h3>Return flow (CRITICAL ΓÇö read before modifying)</h3>
 * <ol>
 *   <li>Customer calls {@code POST /api/v1/orders/{orderId}/returns} with reason.</li>
 *   <li>{@link #initiateReturn} validates: order DELIVERED, within 7-day window,
 *       no active return already exists.</li>
 *   <li>Admin calls {@code PUT /api/v1/admin/returns/{returnId}/review}.</li>
 *   <li>{@link #adminReviewReturn} handles approve/reject:
 *       <ul>
 *         <li>Reject: status ΓåÆ REJECTED, order unchanged.</li>
 *         <li>Approve: Razorpay refund API called (OUTSIDE @Transactional ΓÇö per
 *             ARCHITECTURE_RULES.md). On success: status ΓåÆ REFUND_COMPLETED, order ΓåÆ
 *             REFUNDED, inventory restored.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Architecture rules observed</h3>
 * <ul>
 *   <li>Razorpay API call in {@link #adminReviewReturn} is OUTSIDE any {@code @Transactional}
 *       boundary ΓÇö keeps DB connections free during external HTTP call.</li>
 *   <li>The DB write after a successful Razorpay call is in its own
 *       {@link #completeRefundAndUpdateOrder} method annotated {@code @Transactional}.</li>
 *   <li>Inventory is restored via the existing {@link InventoryReservationService#restore}
 *       method ΓÇö same as the Phase 6 cancellation flow.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReturnServiceImpl implements ReturnService {

    /** Return window in days ΓÇö customers have this many days from delivery to submit a return. */
    private static final int RETURN_WINDOW_DAYS = 7;

    private final ReturnRequestRepository returnRepository;
    private final OrderRepository         orderRepository;
    private final InventoryReservationService reservationService;
    private final RazorpayClient          razorpayClient;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${razorpay.key-id}")
    private String razorpayKeyId;

    // ΓöÇΓöÇ Customer: initiate return ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    /**
     * Customer submits a return request for a delivered order.
     *
     * <p>Guards enforced (in order):
     * <ol>
     *   <li>Order exists and belongs to the authenticated user (404 if not).</li>
     *   <li>Order status is {@code DELIVERED} (409 if not).</li>
     *   <li>Return window has not expired (409 if &gt; {@value RETURN_WINDOW_DAYS} days since the
     *       {@code DELIVERED} status history entry ΓÇö NOT {@code order.updatedAt}).</li>
     *   <li>No active (non-rejected) return already exists for this order (409 if exists).</li>
     * </ol>
     *
     * @param user    the authenticated customer
     * @param orderId the order to return
     * @param request return reason + optional detail
     * @return the created return request response
     */
    @Transactional
    public ReturnRequestResponse initiateReturn(User user, Long orderId, InitiateReturnRequest request) {
        // 1. Load order WITH statusHistory eagerly ΓÇö required to extract the DELIVERED timestamp
        //    without hitting a LazyInitializationException outside the session.
        Order order = orderRepository.findByIdAndUserIdWithStatusHistory(orderId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: id=" + orderId));

        // 2. Guard: only DELIVERED orders can be returned
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException(
                    "Only DELIVERED orders are eligible for a return. " +
                    "Current order status: " + order.getStatus());
        }

        // 3. Guard: return window check ΓÇö 7 days from the DELIVERED status transition.
        //    Source of truth: the OrderStatusHistory entry where status = DELIVERED.
        //    We do NOT use order.getUpdatedAt() because that timestamp changes on every
        //    subsequent mutation (tracking updates, admin notes, etc.) and would give a
        //    wrong baseline for the 7-day window.
        Instant deliveredAt = order.getStatusHistory().stream()
                .filter(h -> h.getStatus() == OrderStatus.DELIVERED)
                .map(com.ego.raw_ego.order.entity.OrderStatusHistory::getCreatedAt)
                .max(Instant::compareTo)   // use the latest DELIVERED entry (defensive ΓÇö normally only one)
                .orElseThrow(() -> new ConflictException(
                        "Cannot determine delivery date: no DELIVERED status entry found for order id=" + orderId +
                        ". This is a data integrity issue ΓÇö please contact support."));

        long daysSinceDelivery = ChronoUnit.DAYS.between(deliveredAt, Instant.now());
        if (daysSinceDelivery > RETURN_WINDOW_DAYS) {
            throw new ConflictException(
                    "Return window has expired. Returns must be submitted within " +
                    RETURN_WINDOW_DAYS + " days of delivery. " +
                    "This order was delivered " + daysSinceDelivery + " days ago.");
        }

        // 4. Guard: only one non-rejected return per order
        if (returnRepository.existsByOrderIdAndStatusNot(orderId, ReturnStatus.REJECTED)) {
            throw new ConflictException(
                    "A return request for this order already exists. " +
                    "Check the status at GET /api/v1/orders/" + orderId + "/returns.");
        }

        // 5. Persist the return request
        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .requestedBy(user)
                .reason(request.getReason())
                .reasonDetail(request.getReasonDetail())
                .status(ReturnStatus.REQUESTED)
                .build();

        ReturnRequest saved = returnRepository.save(returnRequest);

        log.info("Return request created: returnId={} orderId={} userId={} reason={}",
                saved.getId(), orderId, user.getId(), request.getReason());

        return toResponse(saved);
    }

    // ΓöÇΓöÇ Customer: view return status ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    /**
     * Customer retrieves the status of their return request for a given order.
     *
     * @param orderId the order ID
     * @param userId  the authenticated customer's ID (ownership enforced)
     * @return the return request status response
     * @throws ResourceNotFoundException if no return request exists for this order/user
     */
    @Transactional(readOnly = true)
    public ReturnRequestResponse getOrderReturn(Long orderId, Long userId) {
        ReturnRequest returnRequest = returnRepository.findByOrderIdAndRequestedById(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No return request found for orderId=" + orderId));
        return toResponse(returnRequest);
    }

    // ΓöÇΓöÇ Admin: list all returns ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    /**
     * Admin retrieves all return requests, optionally filtered by status.
     *
     * @param status   optional status filter ΓÇö null returns all requests
     * @param pageable pagination parameters
     * @return page of return request responses
     */
    @Transactional(readOnly = true)
    public Page<ReturnRequestResponse> adminGetReturns(ReturnStatus status, Pageable pageable) {
        return returnRepository.findAllByStatusFilter(status, pageable)
                .map(this::toResponse);
    }

    /**
     * Admin retrieves a specific return request by ID.
     *
     * @param returnId the return request ID
     * @return return request detail response
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public ReturnRequestResponse adminGetReturn(Long returnId) {
        ReturnRequest returnRequest = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Return request not found: id=" + returnId));
        return toResponse(returnRequest);
    }

    // ΓöÇΓöÇ Admin: approve or reject ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    /**
     * Admin approves or rejects a return request.
     *
     * <h3>Rejection path</h3>
     * <p>Immediately sets status ΓåÆ {@code REJECTED}. No Razorpay call. Order unchanged.
     *
     * <h3>Approval path (CRITICAL ΓÇö read before modifying)</h3>
     * <p>Steps:
     * <ol>
     *   <li>Validate refundAmount &gt; 0 and Γëñ order.grandTotal.</li>
     *   <li>Set status ΓåÆ {@code APPROVED}, persist (outside @Transactional).</li>
     *   <li>Load order with items for inventory restoration data.</li>
     *   <li>Call Razorpay refund API ({@link #callRazorpayRefund}) ΓÇö <b>OUTSIDE @Transactional</b>.</li>
     *   <li>On success: call {@link #completeRefundAndUpdateOrder} ΓÇö <b>inside @Transactional</b>:
     *       <ul>
     *         <li>Store {@code razorpayRefundId} on the return request.</li>
     *         <li>Set return status ΓåÆ {@code REFUND_COMPLETED}.</li>
     *         <li>Advance order status ΓåÆ {@code REFUNDED}.</li>
     *         <li>Append {@code OrderStatusHistory(REFUNDED)} audit entry.</li>
     *         <li>Restore inventory: {@code quantity_available += quantity} for each item.</li>
     *         <li>{@code saveAndFlush()} both entities.</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * @param returnId the return request to review
     * @param request  approve/reject decision + refundAmount + adminNotes
     * @return updated return request response
     */
    public ReturnRequestResponse adminReviewReturn(Long returnId, AdminReviewReturnRequest request) {
        // 1. Load return request ΓÇö no @Transactional yet
        ReturnRequest returnRequest = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Return request not found: id=" + returnId));

        // 2. Guard: only REQUESTED or APPROVED (retry) returns can be reviewed
        if (returnRequest.getStatus() != ReturnStatus.REQUESTED
                && returnRequest.getStatus() != ReturnStatus.APPROVED) {
            throw new ConflictException(
                    "This return request cannot be reviewed. " +
                    "Current status: " + returnRequest.getStatus() +
                    ". Only REQUESTED or APPROVED (retry) returns are eligible for admin review.");
        }

        // ΓöÇΓöÇ REJECTION path ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        if (Boolean.FALSE.equals(request.getApprove())) {
            return rejectReturn(returnRequest, request.getAdminNotes());
        }

        // ΓöÇΓöÇ APPROVAL path ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

        // 3. Validate refundAmount is present (required on approve)
        if (request.getRefundAmount() == null || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ConflictException("Refund amount is required and must be greater than 0 when approving a return.");
        }

        // 4. Load order with items ΓÇö need grandTotal for cap validation + items for inventory restore
        Order order = orderRepository.findByIdWithUserAndItems(returnRequest.getOrder().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: id=" + returnRequest.getOrder().getId()));

        // 5. Guard: refundAmount must not exceed original order grandTotal
        if (request.getRefundAmount().compareTo(order.getGrandTotal()) > 0) {
            throw new ConflictException(
                    "Refund amount Γé╣" + request.getRefundAmount() +
                    " exceeds order grand total Γé╣" + order.getGrandTotal() + ".");
        }

        // 6. Guard: order must have a Razorpay payment ID (i.e., was paid online)
        if (order.getRazorpayPaymentId() == null || order.getRazorpayPaymentId().isBlank()) {
            throw new ConflictException(
                    "Cannot process Razorpay refund: no payment ID found on order id=" +
                    order.getId() + ". Was this order paid via Razorpay?");
        }

        // 7. Mark as APPROVED (transient state) before making the Razorpay API call
        markApproved(returnRequest, request.getRefundAmount(), request.getAdminNotes());

        // 8. Call Razorpay refund API ΓÇö OUTSIDE @Transactional (per ARCHITECTURE_RULES.md)
        long amountInPaise = request.getRefundAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();
        String razorpayRefundId = callRazorpayRefund(order.getRazorpayPaymentId(), amountInPaise, returnRequest.getId());

        // 9. All DB writes after successful Razorpay call ΓÇö inside @Transactional
        List<OrderItem> lineItems = new ArrayList<>(order.getItems());
        return completeRefundAndUpdateOrder(returnRequest.getId(), order.getId(), razorpayRefundId, lineItems);
    }

    // ΓöÇΓöÇ Internal helpers ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    /**
     * Persists a rejection decision. {@code @Transactional} ΓÇö single DB write.
     */
    @Transactional
    protected ReturnRequestResponse rejectReturn(ReturnRequest returnRequest, String adminNotes) {
        returnRequest.setStatus(ReturnStatus.REJECTED);
        returnRequest.setAdminNotes(adminNotes);
        ReturnRequest saved = returnRepository.save(returnRequest);

        log.info("Return request REJECTED: returnId={} orderId={}",
                returnRequest.getId(), returnRequest.getOrder().getId());
        return toResponse(saved);
    }

    /**
     * Sets the return request to {@code APPROVED} with refund amount and admin notes.
     * Runs in its own {@code @Transactional} to commit the intermediate state before
     * the Razorpay API call. If Razorpay later fails, the APPROVED state persists as
     * an audit record ΓÇö the admin can retry.
     */
    @Transactional
    protected void markApproved(ReturnRequest returnRequest, BigDecimal refundAmount, String adminNotes) {
        returnRequest.setStatus(ReturnStatus.APPROVED);
        returnRequest.setRefundAmount(refundAmount);
        returnRequest.setAdminNotes(adminNotes);
        returnRepository.save(returnRequest);
        log.info("Return request APPROVED (awaiting Razorpay refund): returnId={}", returnRequest.getId());
    }

    /**
     * Calls the Razorpay refund API.
     *
     * <p>MUST be called OUTSIDE any {@code @Transactional} boundary ΓÇö per ARCHITECTURE_RULES.md:
     * "Do not perform external HTTP calls inside a database transactional context."
     *
     * @param razorpayPaymentId the Razorpay payment ID from the original payment
     * @param amountInPaise     refund amount in paise (Γé╣1 = 100 paise)
     * @param returnId          used for logging only
     * @return the Razorpay refund ID (format: {@code rfnd_XXXXXXXXXXXXXXXXXX})
     */
    private String callRazorpayRefund(String razorpayPaymentId, long amountInPaise, Long returnId) {
        // ΓöÇΓöÇ Test-mode short-circuit ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        // Razorpay's test mode does NOT support API-initiated refunds for test payments.
        // Attempting it returns BAD_REQUEST_ERROR. Instead we simulate a successful refund
        // so the entire approval flow can be exercised end-to-end during development.
        if (razorpayKeyId != null && razorpayKeyId.startsWith("rzp_test_")) {
            String simulatedRefundId = "rfnd_TEST_" + returnId + "_" + System.currentTimeMillis();
            log.warn("[TEST MODE] Simulating Razorpay refund: returnId={} paymentId={} simulatedRefundId={} amountPaise={}",
                    returnId, razorpayPaymentId, simulatedRefundId, amountInPaise);
            return simulatedRefundId;
        }

        // ΓöÇΓöÇ Live mode: real Razorpay API call ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        try {
            JSONObject refundOptions = new JSONObject();
            refundOptions.put("amount", amountInPaise);
            refundOptions.put("speed", "normal"); // "normal" = 5ΓÇô7 business days, "optimum" = instant

            com.razorpay.Refund refund = razorpayClient.payments.refund(razorpayPaymentId, refundOptions);
            String razorpayRefundId = refund.get("id").toString();

            log.info("Razorpay refund initiated: returnId={} razorpayPaymentId={} razorpayRefundId={} amountPaise={}",
                    returnId, razorpayPaymentId, razorpayRefundId, amountInPaise);

            return razorpayRefundId;

        } catch (RazorpayException e) {
            log.error("Razorpay refund failed: returnId={} razorpayPaymentId={} error={}",
                    returnId, razorpayPaymentId, e.getMessage());
            throw new ConflictException(
                    "Payment gateway error: unable to process refund. " +
                    "The return request is marked APPROVED ΓÇö please retry. Details: " + e.getMessage());
        }
    }

    /**
     * Completes all DB updates after a successful Razorpay refund call.
     *
     * <p>All mutations run in a single {@code @Transactional} boundary:
     * <ul>
     *   <li>Return status ΓåÆ {@code REFUND_INITIATED} ΓåÆ {@code REFUND_COMPLETED}</li>
     *   <li>{@code razorpayRefundId} stored on the return request</li>
     *   <li>Order status ΓåÆ {@code REFUNDED}</li>
     *   <li>{@code OrderStatusHistory(REFUNDED)} appended</li>
     *   <li>{@code quantity_available += quantity} for each order item (inventory restore)</li>
     * </ul>
     *
     * <p><b>Inventory restore note:</b> {@link InventoryReservationService#restore} uses
     * {@code @Modifying(clearAutomatically=true)}, which calls {@code em.clear()} after
     * each bulk UPDATE. To avoid {@code LazyInitializationException}, the caller must
     * snapshot {@code order.getItems()} into a plain {@code ArrayList} BEFORE calling this
     * method (which the caller {@link #adminReviewReturn} does).
     *
     * @param returnRequestId  ID to reload the ReturnRequest fresh (post em.clear())
     * @param orderId          ID to reload the Order fresh (post em.clear())
     * @param razorpayRefundId Razorpay refund ID from the API response
     * @param lineItems        snapshot of order items (pre-loaded before any em.clear())
     * @return the final return request response
     */
    @Transactional
    protected ReturnRequestResponse completeRefundAndUpdateOrder(
            Long returnRequestId,
            Long orderId,
            String razorpayRefundId,
            List<OrderItem> lineItems) {

        // ΓöÇΓöÇ Restore inventory for each line item ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        // Each restore() call uses @Modifying(clearAutomatically=true) ΓåÆ em.clear()
        // evicts ALL entities. We iterate lineItems (already a plain List snapshot)
        // so no lazy-load issues during the loop.
        for (OrderItem item : lineItems) {
            reservationService.restore(item.getVariantId(), item.getQuantity());
        }

        // ΓöÇΓöÇ Re-fetch fresh managed entities after em.clear() ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        // The original returnRequest and order references are now detached.
        ReturnRequest returnRequest = returnRepository.findById(returnRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Return request not found after refund: id=" + returnRequestId));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found after refund: id=" + orderId));

        // ΓöÇΓöÇ Update return request ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        returnRequest.setStatus(ReturnStatus.REFUND_COMPLETED);
        returnRequest.setRazorpayRefundId(razorpayRefundId);

        // ΓöÇΓöÇ Advance order status ΓåÆ REFUNDED ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        order.setStatus(OrderStatus.REFUNDED);
        OrderStatusHistory refundedHistory = OrderStatusHistory.builder()
                .status(OrderStatus.REFUNDED)
                .note("Refund processed via Razorpay. Refund ID: " + razorpayRefundId)
                .build();
        order.addStatusHistory(refundedHistory);

        // saveAndFlush() forces immediate DB roundtrip ΓÇö ensures @CreationTimestamp
        // on the new OrderStatusHistory entry is populated before DTO mapping.
        orderRepository.saveAndFlush(order);
        ReturnRequest savedReturn = returnRepository.save(returnRequest);

        log.info("Refund COMPLETED: returnId={} orderId={} razorpayRefundId={}",
                returnRequestId, orderId, razorpayRefundId);

        // ΓöÇΓöÇ Publish REFUND_COMPLETED notification event ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ
        // Published AFTER saveAndFlush() so the DB record is committed before
        // the async listener opens its own Hibernate session to read the order.
        eventPublisher.publishEvent(
                new RefundCompletedEvent(this, orderId, order.getUser().getId(), razorpayRefundId));

        return toResponse(savedReturn);
    }

    // ΓöÇΓöÇ DTO mapper ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    private ReturnRequestResponse toResponse(ReturnRequest r) {
        return ReturnRequestResponse.builder()
                .id(r.getId())
                .orderId(r.getOrder().getId())
                .requestedById(r.getRequestedBy().getId())
                .status(r.getStatus())
                .reason(r.getReason())
                .reasonDetail(r.getReasonDetail())
                .refundAmount(r.getRefundAmount())
                .razorpayRefundId(r.getRazorpayRefundId())
                .adminNotes(r.getAdminNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}

