package com.ego.raw_ego.returns.enums;

/**
 * Reason categories for a return request.
 *
 * <p>The customer selects one reason code. An optional free-text
 * {@code reasonDetail} field on {@code ReturnRequest} allows elaboration.
 */
public enum ReturnReason {

    /** Item arrived broken, torn, or otherwise not functional. */
    DEFECTIVE,

    /** A different product or variant was shipped. */
    WRONG_ITEM,

    /** Variant size/fit does not match what was ordered. */
    SIZE_ISSUE,

    /** Product does not match its listing description or images. */
    NOT_AS_DESCRIBED,

    /** Any other reason — customer details in {@code reasonDetail}. */
    OTHER
}
