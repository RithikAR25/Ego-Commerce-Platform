package com.ego.raw_ego.cart.service;

import com.ego.raw_ego.cart.dto.request.AddToCartRequest;
import com.ego.raw_ego.cart.dto.request.UpdateCartItemRequest;
import com.ego.raw_ego.cart.dto.response.CartItemResponse;
import com.ego.raw_ego.cart.dto.response.CartResponse;
import com.ego.raw_ego.catalog.entity.AttributeValue;
import com.ego.raw_ego.catalog.entity.InventoryRecord;
import com.ego.raw_ego.catalog.entity.ProductVariant;
import com.ego.raw_ego.catalog.entity.VariantImage;
import com.ego.raw_ego.catalog.repository.ProductVariantRepository;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of {@link CartService} — Redis-backed shopping cart with MySQL inventory reservation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final String CART_KEY_PREFIX      = "cart:";
    private static final String ANON_CART_KEY_PREFIX = "cart:anon:";

    private final RedisTemplate<String, String>   redisTemplate;
    private final ProductVariantRepository        variantRepository;
    private final InventoryReservationService     reservationService;

    @Value("${ego.cart.ttl-days:7}")
    private int cartTtlDays;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);
        return buildCartResponse(rawCart);
    }

    @Override
    @Transactional
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        Long variantId    = request.getVariantId();
        int  requestedQty = request.getQuantity();

        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product variant not found: id=" + variantId));

        if (!variant.isActive()) {
            throw new ConflictException("This variant is no longer available.");
        }

        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);

        int currentQty = rawCart.getOrDefault(variantId, 0);
        int newQty     = currentQty + requestedQty;

        if (newQty > 10) {
            throw new ConflictException(
                    "Maximum 10 units per item. You already have " + currentQty +
                    " in your cart.");
        }

        reservationService.reserve(variantId, requestedQty);

        rawCart.put(variantId, newQty);
        saveRawCart(cartKey, rawCart);

        log.info("Cart add: userId={} variantId={} qty={} (total={})", userId, variantId, requestedQty, newQty);
        return buildCartResponse(rawCart);
    }

    @Override
    @Transactional
    public CartResponse updateCartItem(Long userId, Long variantId, UpdateCartItemRequest request) {
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);

        if (!rawCart.containsKey(variantId)) {
            throw new ResourceNotFoundException("Item not found in cart: variantId=" + variantId);
        }

        int currentQty = rawCart.get(variantId);
        int newQty     = request.getQuantity();
        int delta      = newQty - currentQty;

        if (delta > 0) {
            reservationService.reserve(variantId, delta);
        } else if (delta < 0) {
            reservationService.release(variantId, -delta);
        }

        rawCart.put(variantId, newQty);
        saveRawCart(cartKey, rawCart);

        log.info("Cart update: userId={} variantId={} qty={}", userId, variantId, newQty);
        return buildCartResponse(rawCart);
    }

    @Override
    @Transactional
    public CartResponse removeCartItem(Long userId, Long variantId) {
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);

        Integer qty = rawCart.remove(variantId);
        if (qty == null) {
            throw new ResourceNotFoundException("Item not found in cart: variantId=" + variantId);
        }

        reservationService.release(variantId, qty);
        saveRawCart(cartKey, rawCart);

        log.info("Cart remove: userId={} variantId={} qty={}", userId, variantId, qty);
        return buildCartResponse(rawCart);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);

        rawCart.forEach((variantId, qty) -> {
            try {
                reservationService.release(variantId, qty);
            } catch (Exception e) {
                log.warn("Could not release reservation for variantId={} during clearCart: {}", variantId, e.getMessage());
            }
        });

        redisTemplate.delete(cartKey);
        log.info("Cart cleared: userId={}", userId);
    }

    @Override
    @Transactional
    public CartResponse mergeAnonymousCart(String sessionId, Long userId) {
        String anonKey = anonCartKey(sessionId);
        String userKey = cartKey(userId);

        Map<Long, Integer> anonCart = loadRawCart(anonKey);
        if (anonCart.isEmpty()) {
            return buildCartResponse(loadRawCart(userKey));
        }

        Map<Long, Integer> userCart = loadRawCart(userKey);

        anonCart.forEach((variantId, anonQty) -> {
            int userQty = userCart.getOrDefault(variantId, 0);
            int mergedQty = Math.max(userQty, anonQty);
            int delta = mergedQty - userQty;

            if (delta > 0) {
                try {
                    reservationService.reserve(variantId, delta);
                    userCart.put(variantId, mergedQty);
                } catch (Exception e) {
                    log.warn("Merge: skipping variantId={} — {}", variantId, e.getMessage());
                }
            }
        });

        saveRawCart(userKey, userCart);
        redisTemplate.delete(anonKey);

        log.info("Cart merged: sessionId={} → userId={}", sessionId, userId);
        return buildCartResponse(userCart);
    }

    // ── Redis helpers ─────────────────────────────────────────────────────────

    private Map<Long, Integer> loadRawCart(String cartKey) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(cartKey);
        Map<Long, Integer> result = new HashMap<>();
        entries.forEach((k, v) -> {
            try {
                result.put(Long.parseLong(k.toString()), Integer.parseInt(v.toString()));
            } catch (NumberFormatException e) {
                log.warn("Malformed cart entry skipped: key={} value={}", k, v);
            }
        });
        return result;
    }

    private void saveRawCart(String cartKey, Map<Long, Integer> cart) {
        if (cart.isEmpty()) {
            redisTemplate.delete(cartKey);
            return;
        }
        redisTemplate.delete(cartKey);
        Map<String, String> toStore = new HashMap<>();
        cart.forEach((k, v) -> toStore.put(k.toString(), v.toString()));
        redisTemplate.opsForHash().putAll(cartKey, toStore);
        redisTemplate.expire(cartKey, Duration.ofDays(cartTtlDays));
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private CartResponse buildCartResponse(Map<Long, Integer> rawCart) {
        if (rawCart.isEmpty()) {
            return CartResponse.builder()
                    .items(List.of())
                    .itemCount(0)
                    .subtotal(BigDecimal.ZERO)
                    .build();
        }

        List<ProductVariant> variants = variantRepository.findAllById(rawCart.keySet());

        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int itemCount = 0;

        for (ProductVariant variant : variants) {
            Integer qty = rawCart.get(variant.getId());
            if (qty == null || qty <= 0) continue;

            InventoryRecord inv = variant.getInventoryRecord();
            String primaryImageUrl = resolvePrimaryImage(variant);
            String variantLabel    = resolveVariantLabel(variant);

            CartItemResponse item = CartItemResponse.builder()
                    .variantId(variant.getId())
                    .sku(variant.getSku())
                    .productName(variant.getProduct().getName())
                    .variantLabel(variantLabel)
                    .price(variant.getPrice())
                    .compareAtPrice(variant.getCompareAtPrice())
                    .discountPercent(variant.getDiscountPercent())
                    .primaryImageUrl(primaryImageUrl)
                    .quantity(qty)
                    .stockStatus(inv != null ? inv.getStockStatus() : InventoryRecord.StockStatus.OUT_OF_STOCK)
                    .quantityAvailable(inv != null ? inv.getQuantityAvailable() : 0)
                    .build();

            items.add(item);
            subtotal = subtotal.add(variant.getPrice().multiply(BigDecimal.valueOf(qty)));
            itemCount += qty;
        }

        return CartResponse.builder()
                .items(items)
                .itemCount(itemCount)
                .subtotal(subtotal)
                .build();
    }

    private String resolvePrimaryImage(ProductVariant variant) {
        if (variant.getImages() != null && !variant.getImages().isEmpty()) {
            return variant.getImages().stream()
                    .filter(VariantImage::isPrimary)
                    .findFirst()
                    .or(() -> variant.getImages().stream().findFirst())
                    .map(VariantImage::getUrl)
                    .orElse(null);
        }
        if (variant.getProduct().getImages() != null && !variant.getProduct().getImages().isEmpty()) {
            return variant.getProduct().getImages().stream()
                    .min((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                    .map(img -> img.getUrl())
                    .orElse(null);
        }
        return null;
    }

    private String resolveVariantLabel(ProductVariant variant) {
        if (variant.getAttributeValues() == null || variant.getAttributeValues().isEmpty()) {
            return variant.getSku();
        }
        return variant.getAttributeValues().stream()
                .map(AttributeValue::getValue)
                .collect(Collectors.joining(" / "));
    }

    private String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    private String anonCartKey(String sessionId) {
        return ANON_CART_KEY_PREFIX + sessionId;
    }
}
