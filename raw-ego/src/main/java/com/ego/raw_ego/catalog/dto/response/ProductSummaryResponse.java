package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.Product;
import com.ego.raw_ego.catalog.entity.VariantImage;
import com.ego.raw_ego.catalog.enums.ProductStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Lightweight product response for listing pages and search results.
 *
 * <p>Does not include full variant matrix — only the minimum needed for a product card:
 * primary image, price range, and available colors for the swatch strip.
 */
@Data
@Builder
public class ProductSummaryResponse {

    private Long id;
    private String name;
    private String slug;
    private ProductStatus status;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal compareAtPrice;    // lowest compare_at_price across variants
    private Integer maxDiscountPercent;   // highest discount % — for the badge
    private String primaryImageUrl;       // hero image URL from the first active variant
    private List<String> tags;
    private Instant createdAt;

    public static ProductSummaryResponse from(Product product) {
        // Find min and max price across active variants
        BigDecimal minPrice = product.getVariants().stream()
                .filter(v -> v.isActive())
                .map(v -> v.getPrice())
                .min(BigDecimal::compareTo)
                .orElse(null);

        BigDecimal maxPrice = product.getVariants().stream()
                .filter(v -> v.isActive())
                .map(v -> v.getPrice())
                .max(BigDecimal::compareTo)
                .orElse(null);

        Integer maxDiscount = product.getVariants().stream()
                .filter(v -> v.isActive())
                .map(v -> v.getDiscountPercent())
                .filter(d -> d != null)
                .max(Integer::compareTo)
                .orElse(null);

        BigDecimal compareAtPrice = product.getVariants().stream()
                .filter(v -> v.isActive() && v.getCompareAtPrice() != null)
                .map(v -> v.getCompareAtPrice())
                .max(BigDecimal::compareTo)
                .orElse(null);

        // Primary image: first primary variant image, or first gallery image
        String primaryImageUrl = product.getVariants().stream()
                .filter(v -> v.isActive())
                .flatMap(v -> v.getImages().stream())
                .filter(VariantImage::isPrimary)
                .findFirst()
                .map(VariantImage::getUrl)
                .or(() -> product.getImages().stream().findFirst().map(i -> i.getUrl()))
                .orElse(null);

        return ProductSummaryResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .slug(product.getSlug())
                .status(product.getStatus())
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .compareAtPrice(compareAtPrice)
                .maxDiscountPercent(maxDiscount)
                .primaryImageUrl(primaryImageUrl)
                .tags(product.getTags())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
