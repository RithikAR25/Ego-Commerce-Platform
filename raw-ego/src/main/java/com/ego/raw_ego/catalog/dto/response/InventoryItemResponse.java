package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.AttributeValue;
import com.ego.raw_ego.catalog.entity.InventoryRecord;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin-only inventory view for a single product variant.
 *
 * <p>Exposes all inventory fields that an admin needs for stock management:
 * available quantity, reserved quantity (held in Redis carts), the low-stock
 * alert threshold, the computed status label, and the parent product context.
 *
 * <p><b>Security:</b> {@code costPrice} is intentionally excluded.
 */
@Data
@Builder
public class InventoryItemResponse {

    private Long   variantId;
    private String sku;

    private Long   productId;
    private String productName;
    private String productSlug;

    /**
     * Human-readable label built from the variant's attribute values.
     * Example: "Black / M"
     */
    private String variantLabel;

    private Integer quantityAvailable;
    private Integer quantityReserved;
    private Integer lowStockThreshold;

    /** IN_STOCK | LOW_STOCK | OUT_OF_STOCK */
    private String stockStatus;

    private Instant updatedAt;

    // ── Factory ──────────────────────────────────────────────────────────────

    public static InventoryItemResponse from(InventoryRecord ir) {
        var variant = ir.getVariant();
        var product = variant.getProduct();

        String label = variant.getAttributeValues().stream()
                .map(AttributeValue::getValue)
                .collect(Collectors.joining(" / "));

        return InventoryItemResponse.builder()
                .variantId(variant.getId())
                .sku(variant.getSku())
                .productId(product.getId())
                .productName(product.getName())
                .productSlug(product.getSlug())
                .variantLabel(label.isBlank() ? variant.getSku() : label)
                .quantityAvailable(ir.getQuantityAvailable())
                .quantityReserved(ir.getQuantityReserved())
                .lowStockThreshold(ir.getLowStockThreshold())
                .stockStatus(ir.getStockStatus().name())
                .updatedAt(ir.getUpdatedAt())
                .build();
    }

    /**
     * Convenience overload: build a list from a page of inventory records.
     */
    public static List<InventoryItemResponse> fromList(List<InventoryRecord> records) {
        return records.stream().map(InventoryItemResponse::from).collect(Collectors.toList());
    }
}
