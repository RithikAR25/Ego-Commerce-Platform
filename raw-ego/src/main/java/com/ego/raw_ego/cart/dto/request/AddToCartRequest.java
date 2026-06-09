package com.ego.raw_ego.cart.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for adding a variant to the cart.
 *
 * <p>Quantity is capped at 10 per cart line to prevent abuse.
 * Server-side, quantity is also validated against available stock
 * before incrementing {@code quantity_reserved} in MySQL.
 */
@Getter
@Setter
@NoArgsConstructor
public class AddToCartRequest {

    @NotNull(message = "variantId is required")
    private Long variantId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "Minimum quantity is 1")
    @Max(value = 10, message = "Maximum quantity per item is 10")
    private Integer quantity;
}
