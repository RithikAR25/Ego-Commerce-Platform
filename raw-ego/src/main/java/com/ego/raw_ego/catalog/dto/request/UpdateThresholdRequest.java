package com.ego.raw_ego.catalog.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request to update <em>only</em> the low-stock alert threshold for a variant.
 *
 * <p>Use {@link UpdateInventoryRequest} when you need to set {@code quantityAvailable}.
 * This separate DTO exists so the PATCH /threshold endpoint is narrowly typed —
 * it cannot accidentally overwrite stock quantities.
 */
@Data
public class UpdateThresholdRequest {

    @NotNull(message = "Low stock threshold is required.")
    @Min(value = 1, message = "Low stock threshold must be at least 1.")
    private Integer lowStockThreshold;
}
