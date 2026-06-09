package com.ego.raw_ego.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for updating the quantity of a specific cart item.
 *
 * <p>To remove an item entirely, use the DELETE endpoint instead of setting quantity=0.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateCartItemRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "Minimum quantity is 1")
    @Max(value = 10, message = "Maximum quantity per item is 10")
    private Integer quantity;
}
