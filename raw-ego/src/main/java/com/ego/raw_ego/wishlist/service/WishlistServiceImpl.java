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
 * Implementation of {@link WishlistService} — business logic for the wishlist module.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private final WishlistItemRepository   wishlistRepository;
    private final ProductVariantRepository variantRepository;

    @Override
    @Transactional(readOnly = true)
    public WishlistResponse getWishlist(Long userId) {
        List<WishlistItem> items = wishlistRepository.findByUserIdOrderByCreatedAtAsc(userId);

        if (items.isEmpty()) {
            return WishlistResponse.builder().items(List.of()).itemCount(0).build();
        }

        List<Long> variantIds = items.stream().map(WishlistItem::getVariantId).toList();
        Map<Long, ProductVariant> variantMap = variantRepository.findAllById(variantIds)
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        List<WishlistItemResponse> responses = items.stream()
                .filter(item -> variantMap.containsKey(item.getVariantId()))
                .map(item -> toItemResponse(item, variantMap.get(item.getVariantId())))
                .toList();

        return WishlistResponse.builder()
                .items(responses)
                .itemCount(responses.size())
                .build();
    }

    @Override
    @Transactional
    public WishlistResponse addItem(Long userId, AddToWishlistRequest request) {
        Long variantId = request.getVariantId();

        if (!variantRepository.existsById(variantId)) {
            throw new ResourceNotFoundException("Variant not found: id=" + variantId);
        }

        if (!wishlistRepository.existsByUserIdAndVariantId(userId, variantId)) {
            WishlistItem item = WishlistItem.builder()
                    .userId(userId)
                    .variantId(variantId)
                    .build();
            wishlistRepository.save(item);
            log.info("Wishlist item added: userId={} variantId={}", userId, variantId);
        } else {
            log.debug("Wishlist add (idempotent skip): userId={} variantId={} already present", userId, variantId);
        }

        return getWishlist(userId);
    }

    @Override
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

    @Override
    @Transactional
    public void clearWishlist(Long userId) {
        wishlistRepository.deleteAllByUserId(userId);
        log.info("Wishlist cleared: userId={}", userId);
    }

    private WishlistItemResponse toItemResponse(WishlistItem item, ProductVariant variant) {
        String primaryImageUrl = variant.getImages().stream()
                .filter(VariantImage::isPrimary)
                .map(VariantImage::getUrl)
                .findFirst()
                .orElseGet(() -> variant.getImages().stream()
                        .map(VariantImage::getUrl)
                        .findFirst()
                        .orElse(null));

        String variantLabel = variant.getAttributeValues().stream()
                .map(av -> av.getValue())
                .collect(Collectors.joining(" / "));

        InventoryRecord.StockStatus stockStatus = InventoryRecord.StockStatus.OUT_OF_STOCK;
        if (variant.getInventoryRecord() != null) {
            stockStatus = variant.getInventoryRecord().getStockStatus();
        }

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
