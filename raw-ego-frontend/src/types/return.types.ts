/**
 * return.types.ts
 *
 * TypeScript interfaces for the Return & Refund domain (Phase 10).
 * These MUST mirror the exact backend DTO field names — never invent field names.
 *
 * Key backend DTOs this maps to:
 *   - ReturnRequestResponse.java
 *   - InitiateReturnRequest.java
 *   - AdminReviewReturnRequest.java
 *   - ReturnReason.java (enum)
 *   - ReturnStatus.java (enum)
 */

// ── Enums ──────────────────────────────────────────────────────────────────────

/**
 * Mirrors ReturnReason.java enum.
 * Customer selects one of these reason categories when submitting a return.
 */
export type ReturnReason =
  | 'DEFECTIVE'
  | 'WRONG_ITEM'
  | 'SIZE_ISSUE'
  | 'NOT_AS_DESCRIBED'
  | 'OTHER';

/**
 * Mirrors ReturnStatus.java enum.
 * Terminal states: REJECTED, REFUND_COMPLETED.
 */
export type ReturnStatus =
  | 'REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'REFUND_INITIATED'
  | 'REFUND_COMPLETED';

// ── Response types ─────────────────────────────────────────────────────────────

/**
 * Matches ReturnRequestResponse.java.
 * Returned by all return request endpoints.
 */
export interface ReturnRequest {
  id:              number;
  orderId:         number;
  requestedById:   number;
  status:          ReturnStatus;
  reason:          ReturnReason;
  /** Optional free-text from the customer. Null if not provided. */
  reasonDetail:    string | null;
  /**
   * Rupee refund amount set by admin at approval time.
   * Null until admin approves the return.
   */
  refundAmount:    number | null;
  /**
   * Razorpay refund ID — e.g. "rfnd_XXXXXXXXXXXXXXXXXX".
   * Null until the Razorpay refund call completes.
   */
  razorpayRefundId: string | null;
  /** Optional admin notes visible to the customer. Null if not added. */
  adminNotes:      string | null;
  createdAt:       string;
  updatedAt:       string;
}

// ── Request payload types ──────────────────────────────────────────────────────

/** Matches InitiateReturnRequest.java — sent by customer to POST /orders/{id}/returns */
export interface InitiateReturnPayload {
  reason: ReturnReason;
  /** Optional elaboration. Required when reason = 'OTHER'. Max 500 chars. */
  reasonDetail?: string;
}

/**
 * Matches AdminReviewReturnRequest.java — sent by admin to PUT /admin/returns/{id}/review
 *
 * When approve = true, refundAmount is required (> 0, ≤ order.grandTotal).
 * When approve = false, refundAmount is ignored.
 */
export interface AdminReviewReturnPayload {
  approve:      boolean;
  /** Required when approve = true. Amount in rupees. */
  refundAmount?: number;
  /** Optional admin notes visible to customer. */
  adminNotes?:   string;
}
