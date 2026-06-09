package com.ego.raw_ego.returns.dto.request;

import com.ego.raw_ego.returns.enums.ReturnReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for {@code POST /api/v1/orders/{orderId}/returns}.
 *
 * <p>Customers submit this to initiate a return for a delivered order.
 * The {@code reason} field is required; {@code reasonDetail} is optional
 * but encouraged when reason = {@code OTHER}.
 */
@Getter
@Setter
@NoArgsConstructor
public class InitiateReturnRequest {

    /**
     * Category of the return reason. Required.
     * One of: DEFECTIVE, WRONG_ITEM, SIZE_ISSUE, NOT_AS_DESCRIBED, OTHER.
     */
    @NotNull(message = "Return reason is required.")
    private ReturnReason reason;

    /**
     * Optional free-text elaboration on the return reason.
     * Especially important when reason = OTHER.
     * Maximum 500 characters.
     */
    @Size(max = 500, message = "Reason detail must not exceed 500 characters.")
    private String reasonDetail;
}
