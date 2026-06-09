package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.AttributeType;
import com.ego.raw_ego.catalog.entity.AttributeValue;
import com.ego.raw_ego.catalog.entity.Product;
import com.ego.raw_ego.catalog.enums.ProductStatus;
import com.ego.raw_ego.catalog.service.StockUrgencyService;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Full product detail response — used for the Product Detail Page (PDP).
 *
 * <p>Contains all variants with their attribute values and images.
 * The frontend uses this to build the variant selector matrix client-side.
 *
 * <p>Price range (min/max across active variants) is pre-computed here
 * so the product listing card can show "From ₹1,299" without loading all variants.
 */
@Data
@Builder
public class ProductDetailResponse {

    private Long id;
    private String name;
    private String slug;
    private String productCode;
    private String description;
    private String material;
    private String careInstructions;
    private ProductStatus status;
    private List<String> tags;
    private CategoryResponse category;

    /** All attribute types and their values — used to build the variant selector. */
    private List<AttributeTypeResponse> attributeTypes;

    /** All active variants with their attribute values and images. */
    private List<VariantResponse> variants;

    /** Gallery-level images (lifestyle shots, not tied to a variant). */
    private List<ImageResponse> galleryImages;

    /** Pre-computed price range across active variants. */
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Admin / internal factory — does NOT enrich variants with urgency fields.
     * Used for admin product detail views (DRAFT/ARCHIVED previews).
     */
    public static ProductDetailResponse from(Product product) {
        List<VariantResponse> variants = product.getVariants().stream()
                .filter(v -> v.isActive())
                .map(VariantResponse::from)
                .collect(Collectors.toList());

        List<ImageResponse> galleryImages = product.getImages().stream()
                .map(ImageResponse::fromProductImage)
                .collect(Collectors.toList());

        List<AttributeTypeResponse> attributeTypes = product.getAttributeTypes().stream()
                .map(AttributeTypeResponse::from)
                .collect(Collectors.toList());

        BigDecimal minPrice = variants.stream()
                .map(VariantResponse::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal maxPrice = variants.stream()
                .map(VariantResponse::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(null);

        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .productCode(product.getProductCode())
                .description(product.getDescription())
                .material(product.getMaterial())
                .careInstructions(product.getCareInstructions())
                .status(product.getStatus())
                .tags(product.getTags())
                .category(CategoryResponse.from(product.getCategory()))
                .attributeTypes(attributeTypes)
                .variants(variants)
                .galleryImages(galleryImages)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    /**
     * Storefront factory — enriches each active variant with stock urgency fields
     * ({@code quantityAvailable}, {@code lowStock}, {@code stockUrgencyMessage}).
     *
     * <p>Use this overload for the Product Detail Page. The {@code urgencyService}
     * is injected by the calling service ({@link com.ego.raw_ego.catalog.service.ProductService})
     * so this method stays purely functional and testable without a Spring context.
     *
     * @param product        the product entity (variants + inventoryRecords must be loaded)
     * @param urgencyService the stock urgency service bean
     * @return a fully enriched {@link ProductDetailResponse}
     */
    public static ProductDetailResponse from(Product product, StockUrgencyService urgencyService) {
        List<VariantResponse> variants = product.getVariants().stream()
                .filter(v -> v.isActive())
                .map(v -> VariantResponse.from(v, urgencyService))
                .collect(Collectors.toList());

        List<ImageResponse> galleryImages = product.getImages().stream()
                .map(ImageResponse::fromProductImage)
                .collect(Collectors.toList());

        List<AttributeTypeResponse> attributeTypes = product.getAttributeTypes().stream()
                .map(AttributeTypeResponse::from)
                .collect(Collectors.toList());

        BigDecimal minPrice = variants.stream()
                .map(VariantResponse::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal maxPrice = variants.stream()
                .map(VariantResponse::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(null);

        return ProductDetailResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .productCode(product.getProductCode())
                .description(product.getDescription())
                .material(product.getMaterial())
                .careInstructions(product.getCareInstructions())
                .status(product.getStatus())
                .tags(product.getTags())
                .category(CategoryResponse.from(product.getCategory()))
                .attributeTypes(attributeTypes)
                .variants(variants)
                .galleryImages(galleryImages)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    @Data
    @Builder
    public static class AttributeTypeResponse {
        private Long id;
        private String name;
        private Integer displayOrder;
        private List<AttributeValueSummary> values;

        public static AttributeTypeResponse from(AttributeType type) {
            return AttributeTypeResponse.builder()
                    .id(type.getId())
                    .name(type.getName())
                    .displayOrder(type.getDisplayOrder())
                    .values(type.getValues().stream()
                            .map(AttributeValueSummary::from)
                            .collect(Collectors.toList()))
                    .build();
        }
    }

    @Data
    @Builder
    public static class AttributeValueSummary {
        private Long id;
        private String value;
        private String code;
        private String hexColor;
        private String swatchImageUrl;
        private Integer displayOrder;

        public static AttributeValueSummary from(AttributeValue av) {
            return AttributeValueSummary.builder()
                    .id(av.getId())
                    .value(av.getValue())
                    .code(av.getCode())
                    .hexColor(av.getHexColor())
                    .swatchImageUrl(av.getSwatchImageUrl())
                    .displayOrder(av.getDisplayOrder())
                    .build();
        }
    }
}
