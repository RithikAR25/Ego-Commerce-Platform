package com.ego.raw_ego.wishlist.service;

import com.ego.raw_ego.catalog.entity.InventoryRecord;
import com.ego.raw_ego.catalog.entity.ProductVariant;
import com.ego.raw_ego.catalog.entity.VariantImage;
import com.ego.raw_ego.catalog.repository.ProductVariantRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.wishlist.dto.request.AddToWishlistRequest;
import com.ego.raw_ego.wishlist.dto.response.WishlistItemResponse;
import com.ego.raw_ego.wishlist.dto.response.WishlistResponse;
import com.ego.raw_ego.wishlist.entity.WishlistItem;
import com.ego.raw_ego.wishlist.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business logic for the wishlist module.
 *
 * <h3>Idempotency</h3>
 * <p>Add: if the variant is already in the wishlist, returns success without creating
 * a duplicate row (no 409 thrown). Remove: if the variant is not in the wishlist,
 * returns success silently. Clear: always succeeds.
 *
 * <h3>Live catalog data</h3>
 * <p>The {@link #getWishlist} method fetches current prices and stock status
 * from the catalog — not snapshots. Prices are subject to change.
 * Deleted variants are silently dropped from the wishlist on read.
 *
 * <h3>Variant validation on add</h3>
 * <p>Adding a non-existent variantId throws {@link ResourceNotFoundException} (404).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository   wishlistRepository;
    private final ProductVariantRepository variantRepository;

    // ── GET wishlist ──────────────────────────────────────────────────────────

    /**
     * Returns the full wishlist for a user with live variant data.
     * Variants that have since been deleted from the catalog are silently dropped.
     */
    @Transactional(readOnly = true)
    public WishlistResponse getWishlist(Long userId) {
        List<WishlistItem> items = wishlistRepository.findByUserIdOrderByCreatedAtAsc(userId);

        if (items.isEmpty()) {
            return WishlistResponse.builder().items(List.of()).itemCount(0).build();
        }

        // Batch-fetch all variants in one SQL call — prevents N+1
        List<Long> variantIds = items.stream().map(WishlistItem::getVariantId).toList();
        Map<Long, ProductVariant> variantMap = variantRepository.findAllById(variantIds)
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<WishlistItemResponse> responses = items.stream()
                .filter(item -> variantMap.containsKey(item.getVariantId())) // drop deleted variants
                .map(item -> toItemResponse(item, variantMap.get(item.getVariantId())))
                .toList();

        return WishlistResponse.builder()
                .items(responses)
                .itemCount(responses.size())
                .build();
    }

    // ── ADD item ──────────────────────────────────────────────────────────────

    /**
     * Adds a variant to the user's wishlist. Idempotent — re-adding an existing
     * variant returns {@code 200 OK} without a duplicate row.
     *
     * @throws ResourceNotFoundException if the variantId does not exist
     */
    @Transactional
    public WishlistResponse addItem(Long userId, AddToWishlistRequest request) {
        Long variantId = request.getVariantId();

        // Validate variant exists
        if (!variantRepository.existsById(variantId)) {
            throw new ResourceNotFoundException("Variant not found: id=" + variantId);
        }

        // Idempotent: skip insert if already wishlisted
        if (!wishlistRepository.existsByUserIdAndVariantId(userId, variantId)) {
            WishlistItem item = WishlistItem.builder()
                    .userId(userId)
                    .variantId(variantId)
                    .build();
            wishlistRepository.save(item);
            log.info("Wishlist item added: userId={} variantId={}", userId, variantId);
        } else {
            log.debug("Wishlist add (idempotent skip): userId={} variantId={} already present",
                    userId, variantId);
        }

        return getWishlist(userId);
    }

    // ── REMOVE item ───────────────────────────────────────────────────────────

    /**
     * Removes a variant from the user's wishlist. Idempotent — removing a variant
     * not in the wishlist returns {@code 200 OK} silently.
     */
    @Transactional
    public WishlistResponse removeItem(Long userId, Long variantId) {
        wishlistRepository.findByUserIdAndVariantId(userId, variantId)
                .ifPresentOrElse(
                        item -> {
                            wishlistRepository.delete(item);
                            log.info("Wishlist item removed: userId={} variantId={}", userId, variantId);
                        },
                        () -> log.debug("Wishlist remove (idempotent skip): userId={} variantId={} not found",
                                userId, variantId)
                );
        return getWishlist(userId);
    }

    // ── CLEAR wishlist ────────────────────────────────────────────────────────

    /**
     * Clears the entire wishlist for a user.
     */
    @Transactional
    public void clearWishlist(Long userId) {
        wishlistRepository.deleteAllByUserId(userId);
        log.info("Wishlist cleared: userId={}", userId);
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private WishlistItemResponse toItemResponse(WishlistItem item, ProductVariant variant) {
        // Primary image URL — first image with primary=true, else first image, else null
        String primaryImageUrl = variant.getImages().stream()
                .filter(VariantImage::isPrimary)
                .map(VariantImage::getUrl)
                .findFirst()
                .orElseGet(() -> variant.getImages().stream()
                        .map(VariantImage::getUrl)
                        .findFirst()
                        .orElse(null));

        // Variant label from attribute values
        String variantLabel = variant.getAttributeValues().stream()
                .map(av -> av.getValue())
                .collect(Collectors.joining(" / "));

        // Stock status from inventory record
        InventoryRecord.StockStatus stockStatus = InventoryRecord.StockStatus.OUT_OF_STOCK;
        if (variant.getInventoryRecord() != null) {
            stockStatus = variant.getInventoryRecord().getStockStatus();
        }

        // Product name
        String productName = variant.getProduct() != null ? variant.getProduct().getName() : "";

        return WishlistItemResponse.builder()
                .id(item.getId())
                .variantId(variant.getId())
                .sku(variant.getSku())
                .productName(productName)
                .variantLabel(variantLabel.isEmpty() ? null : variantLabel)
                .price(variant.getPrice())
                .compareAtPrice(variant.getCompareAtPrice())
                .discountPercent(variant.getDiscountPercent())
                .primaryImageUrl(primaryImageUrl)
                .stockStatus(stockStatus)
                .addedAt(item.getCreatedAt())
                .build();
    }
}
