package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.AttributeValue;
import com.ego.raw_ego.catalog.entity.InventoryRecord;
import com.ego.raw_ego.catalog.entity.ProductVariant;
import com.ego.raw_ego.catalog.entity.VariantImage;
import com.ego.raw_ego.catalog.service.StockUrgencyService;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Public-facing variant response.
 *
 * <p><b>Security note:</b> {@code costPrice} is NEVER included here.
 * It is only available in the separate AdminVariantResponse used in admin APIs.
 *
 * <p>Discount percent is computed dynamically — never stored.
 *
 * <p><b>Stock urgency fields ({@code quantityAvailable}, {@code lowStock},
 * {@code stockUrgencyMessage}):</b> populated only when the enriched
 * {@link #from(ProductVariant, StockUrgencyService)} factory is used (storefront PDP).
 * The single-argument {@link #from(ProductVariant)} overload leaves them null/false,
 * which is the correct behaviour for admin variant management responses.
 */
@Data
@Builder
public class VariantResponse {

    private Long id;
    private String sku;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private Integer discountPercent;       // computed: null when no compareAtPrice
    private boolean active;
    private String stockStatus;            // IN_STOCK | LOW_STOCK | OUT_OF_STOCK
    private Integer weightGrams;

    // ── Stock urgency fields (storefront PDP only) ────────────────────────────

    /**
     * Units currently available for purchase.
     * Populated only on the storefront product detail response.
     * NOTE: costPrice and quantityReserved are intentionally NOT included.
     */
    private Integer quantityAvailable;

    /** True when quantity warrants an urgency signal (qty 0–10). */
    private boolean lowStock;

    /**
     * Human-readable scarcity message for the storefront.
     * Examples: "Only 3 left in your size" | "Selling fast" | null (no urgency).
     */
    private String stockUrgencyMessage;

    private List<AttributeValueResponse> attributeValues;
    private List<ImageResponse> images;

    /**
     * Admin / internal factory — does NOT populate stock urgency fields.
     * Used by admin variant create/update paths where urgency data is irrelevant.
     */
    public static VariantResponse from(ProductVariant variant) {
        InventoryRecord inv = variant.getInventoryRecord();
        String stockStatus = inv != null
                ? inv.getStockStatus().name()
                : InventoryRecord.StockStatus.OUT_OF_STOCK.name();

        return VariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .price(variant.getPrice())
                .compareAtPrice(variant.getCompareAtPrice())
                .discountPercent(variant.getDiscountPercent())
                .active(variant.isActive())
                .stockStatus(stockStatus)
                .weightGrams(variant.getWeightGrams())
                .attributeValues(variant.getAttributeValues().stream()
                        .map(AttributeValueResponse::from)
                        .collect(Collectors.toList()))
                .images(variant.getImages().stream()
                        .map(ImageResponse::fromVariantImage)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Storefront factory — populates all stock urgency fields via
     * {@link StockUrgencyService}.
     *
     * <p>Use this overload for the Product Detail Page response. The urgency
     * service is passed in (not looked up) so this method remains testable
     * without a Spring context.
     *
     * @param variant        the product variant entity (must have inventoryRecord loaded)
     * @param urgencyService the stock urgency service bean
     * @return a fully enriched {@link VariantResponse}
     */
    public static VariantResponse from(ProductVariant variant, StockUrgencyService urgencyService) {
        InventoryRecord inv = variant.getInventoryRecord();
        String stockStatus = inv != null
                ? inv.getStockStatus().name()
                : InventoryRecord.StockStatus.OUT_OF_STOCK.name();

        int qty = (inv != null) ? inv.getQuantityAvailable() : 0;
        StockUrgencyService.StockUrgencyResult urgency = urgencyService.computeUrgency(qty);

        return VariantResponse.builder()
                .id(variant.getId())
                .sku(variant.getSku())
                .price(variant.getPrice())
                .compareAtPrice(variant.getCompareAtPrice())
                .discountPercent(variant.getDiscountPercent())
                .active(variant.isActive())
                .stockStatus(stockStatus)
                .weightGrams(variant.getWeightGrams())
                .quantityAvailable(urgency.quantityAvailable())
                .lowStock(urgency.lowStock())
                .stockUrgencyMessage(urgency.stockUrgencyMessage())
                .attributeValues(variant.getAttributeValues().stream()
                        .map(AttributeValueResponse::from)
                        .collect(Collectors.toList()))
                .images(variant.getImages().stream()
                        .map(ImageResponse::fromVariantImage)
                        .collect(Collectors.toList()))
                .build();
    }

    @Data
    @Builder
    public static class AttributeValueResponse {
        private Long id;
        private Long typeId;
        private String typeName;
        private String value;
        private String code;
        private String hexColor;
        private String swatchImageUrl;

        public static AttributeValueResponse from(AttributeValue av) {
            return AttributeValueResponse.builder()
                    .id(av.getId())
                    .typeId(av.getAttributeType().getId())
                    .typeName(av.getAttributeType().getName())
                    .value(av.getValue())
                    .code(av.getCode())
                    .hexColor(av.getHexColor())
                    .swatchImageUrl(av.getSwatchImageUrl())
                    .build();
        }
    }
}
