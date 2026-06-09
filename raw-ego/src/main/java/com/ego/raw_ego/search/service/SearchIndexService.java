package com.ego.raw_ego.search.service;

import com.ego.raw_ego.catalog.entity.*;
import com.ego.raw_ego.catalog.enums.ProductStatus;
import com.ego.raw_ego.catalog.repository.ProductRepository;
import com.ego.raw_ego.review.repository.ProductReviewRepository;
import com.ego.raw_ego.search.document.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Assembles a {@link ProductDocument} from MySQL data.
 *
 * <p>This service is the bridge between the MySQL catalog and the Elasticsearch index.
 * It is responsible for:
 * <ul>
 *   <li>Loading the Product with its variants, images, and attribute values</li>
 *   <li>Denormalising variant data (sizes, colors, stock) into flat arrays</li>
 *   <li>Fetching the average rating and review count from the reviews module</li>
 *   <li>Enforcing the visibility guard (DRAFT/ARCHIVED → isActive=false)</li>
 * </ul>
 *
 * <p><b>Category path (3-level hierarchy):</b>
 * {@code categoryPath} and {@code categorySlugPath} are built by traversing the parent chain
 * from the product's LEAF category up to ROOT. This enables ES filtering at any level
 * using a simple {@code term(categorySlugPath, slug)} query.
 *
 * <p><b>Visibility guard (CRITICAL):</b>
 * Only ACTIVE and OUT_OF_STOCK products get {@code isActive=true}.
 * DRAFT and ARCHIVED products are indexed with {@code isActive=false}.
 * SearchService always filters on {@code isActive=true} — so these products
 * never appear in any search result.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SearchIndexService {

    private final ProductRepository       productRepository;
    private final ProductReviewRepository reviewRepository;

    /**
     * Builds an ES document for the given product ID by loading all data from MySQL.
     *
     * @param productId the product to index
     * @return populated {@link ProductDocument}, or empty if product not found
     */
    @Transactional(readOnly = true)
    public Optional<ProductDocument> buildDocument(Long productId) {
        return productRepository.findById(productId).map(this::toDocument);
    }

    /**
     * Builds documents for a batch of product IDs.
     * Products not found in MySQL are silently skipped.
     */
    @Transactional(readOnly = true)
    public List<ProductDocument> buildDocuments(List<Long> productIds) {
        List<Product> products = productRepository.findAllById(productIds);
        return products.stream().map(this::toDocument).toList();
    }

    // ── Private mapping ───────────────────────────────────────────────────────

    private ProductDocument toDocument(Product product) {
        List<ProductVariant> activeVariants = product.getVariants().stream()
                .filter(ProductVariant::isActive)
                .toList();

        // ── Denormalise sizes ──────────────────────────────────────────────
        List<String> sizes = activeVariants.stream()
                .flatMap(v -> v.getAttributeValues().stream())
                .filter(av -> "Size".equalsIgnoreCase(av.getAttributeType().getName()))
                .map(AttributeValue::getValue)
                .distinct()
                .toList();

        // ── Denormalise colors (display names + hex codes in matching order) ──
        List<String> colors    = new ArrayList<>();
        List<String> hexCodes  = new ArrayList<>();
        activeVariants.stream()
                .flatMap(v -> v.getAttributeValues().stream())
                .filter(av -> "Color".equalsIgnoreCase(av.getAttributeType().getName()))
                .filter(av -> colors.stream().noneMatch(c -> c.equals(av.getValue())))
                .forEach(av -> {
                    colors.add(av.getValue());
                    hexCodes.add(av.getHexColor() != null ? av.getHexColor() : "");
                });

        // ── Price range ────────────────────────────────────────────────────
        double minPrice = activeVariants.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .min().orElse(0.0);
        double maxPrice = activeVariants.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .max().orElse(0.0);

        // ── Total stock ────────────────────────────────────────────────────
        int totalStock = activeVariants.stream()
                .mapToInt(v -> v.getInventoryRecord() != null
                        ? v.getInventoryRecord().getQuantityAvailable() : 0)
                .sum();

        // ── Primary image ──────────────────────────────────────────────────
        String primaryImageUrl = activeVariants.stream()
                .flatMap(v -> v.getImages().stream())
                .filter(VariantImage::isPrimary)
                .findFirst()
                .map(VariantImage::getUrl)
                .or(() -> product.getImages().stream().findFirst().map(ProductImage::getUrl))
                .orElse(null);

        // ── Reviews ────────────────────────────────────────────────────────
        Double avgRatingRaw = reviewRepository.findAverageRatingByProductId(product.getId());
        float  avgRating    = avgRatingRaw != null ? avgRatingRaw.floatValue() : 0.0f;
        int    reviewCount  = (int) reviewRepository.countByProductId(product.getId());

        // ── Visibility guard ───────────────────────────────────────────────
        // ONLY ACTIVE and OUT_OF_STOCK products are visible in search.
        // DRAFT and ARCHIVED are indexed with isActive=false — they are never
        // returned by SearchService which always filters on isActive=true.
        boolean isActive = product.getStatus() == ProductStatus.ACTIVE
                        || product.getStatus() == ProductStatus.OUT_OF_STOCK;

        // —— Category path (ROOT → GROUP → LEAF ancestry) —————————————————
        // Traverse parent chain from LEAF up to ROOT and build ordered path arrays.
        // Example: LEAF=T-Shirts, GROUP=Topwear, ROOT=MEN
        //   → categoryPath     = ["MEN", "Topwear", "T-Shirts"]
        //   → categorySlugPath = ["men", "topwear", "t-shirts"]
        List<String> categoryPath     = new ArrayList<>();
        List<String> categorySlugPath = new ArrayList<>();
        Category cat = product.getCategory();
        while (cat != null) {
            categoryPath.add(0, cat.getName());
            categorySlugPath.add(0, cat.getSlug());
            cat = cat.getParent();
        }

        return ProductDocument.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .slug(product.getSlug())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .categoryPath(categoryPath)
                .categorySlugPath(categorySlugPath)
                .tags(product.getTags())
                .primaryImageUrl(primaryImageUrl)
                .availableSizes(sizes)
                .availableColors(colors)
                .colorHexCodes(hexCodes)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalStock(totalStock)
                .avgRating(avgRating)
                .reviewCount(reviewCount)
                .isActive(isActive)
                .createdAt(product.getCreatedAt())
                .build();
    }
}
