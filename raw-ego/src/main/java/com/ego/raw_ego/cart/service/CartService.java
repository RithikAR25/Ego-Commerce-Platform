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
 * Core cart business logic — Redis-backed shopping cart with MySQL inventory reservation.
 *
 * <p><b>Redis data model:</b>
 * <pre>
 *   Key:   "cart:{userId}"  (authenticated) | "cart:anon:{sessionId}"  (anonymous)
 *   Type:  Hash
 *   Field: "{variantId}"   (string-coerced Long)
 *   Value: "{quantity}"    (string-coerced Integer)
 *   TTL:   7 days, refreshed on every mutation
 * </pre>
 *
 * <p><b>MySQL inventory reservation:</b>
 * Every cart add/update/remove also adjusts {@code quantity_reserved} in the
 * {@code inventory_records} table to provide admin-facing stock visibility.
 * The actual oversell guard is the {@code @Version} optimistic lock on
 * {@link InventoryRecord} — enforced via {@link InventoryReservationService}.
 *
 * <p><b>Cart read (buildCartResponse):</b>
 * Variants are fetched from MySQL in a single batch call ({@code findAllById})
 * — never N+1. Variants deleted from the catalog are silently dropped from
 * the cart response (Redis state is cleaned lazily on next write).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CartService {

    // ── Key prefixes ──────────────────────────────────────────────────────────

    private static final String CART_KEY_PREFIX     = "cart:";
    private static final String ANON_CART_KEY_PREFIX = "cart:anon:";

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final RedisTemplate<String, String>   redisTemplate;
    private final ProductVariantRepository        variantRepository;
    private final InventoryReservationService     reservationService;

    @Value("${ego.cart.ttl-days:7}")
    private int cartTtlDays;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current cart for the authenticated user.
     * Dead variants (deleted from catalog) are silently omitted.
     *
     * @param userId the authenticated user's primary key
     */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);
        return buildCartResponse(rawCart);
    }

    /**
     * Adds a variant to the cart (or increments quantity if already present).
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate the variant exists and is active</li>
     *   <li>Check that total quantity does not exceed 10 per line</li>
     *   <li>Reserve the delta in MySQL {@code quantity_reserved}</li>
     *   <li>Write the updated cart to Redis and refresh TTL</li>
     * </ol>
     *
     * @param userId  the authenticated user's primary key
     * @param request add-to-cart payload
     */
    @Transactional
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        Long variantId = request.getVariantId();
        int  requestedQty = request.getQuantity();

        // 1. Validate variant exists and is active
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product variant not found: id=" + variantId));

        if (!variant.isActive()) {
            throw new ConflictException("This variant is no longer available.");
        }

        // 2. Load current cart and compute new quantity
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);

        int currentQty = rawCart.getOrDefault(variantId, 0);
        int newQty     = currentQty + requestedQty;

        if (newQty > 10) {
            throw new ConflictException(
                    "Maximum 10 units per item. You already have " + currentQty +
                    " in your cart.");
        }

        // 3. Reserve the delta in inventory (only the new units, not the total)
        reservationService.reserve(variantId, requestedQty);

        // 4. Persist cart to Redis
        rawCart.put(variantId, newQty);
        saveRawCart(cartKey, rawCart);

        log.info("Cart add: userId={} variantId={} qty={} (total={})", userId, variantId, requestedQty, newQty);
        return buildCartResponse(rawCart);
    }

    /**
     * Sets the quantity of a specific cart line item to an absolute value.
     *
     * <p>The difference from the current quantity is reserved or released accordingly.
     *
     * @param userId    the authenticated user
     * @param variantId the cart line item to update
     * @param request   new absolute quantity
     */
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
        // delta == 0 → no change, still refresh TTL

        rawCart.put(variantId, newQty);
        saveRawCart(cartKey, rawCart);

        log.info("Cart update: userId={} variantId={} qty={}", userId, variantId, newQty);
        return buildCartResponse(rawCart);
    }

    /**
     * Removes a specific variant from the cart and releases its inventory reservation.
     *
     * @param userId    the authenticated user
     * @param variantId the variant to remove
     */
    @Transactional
    public CartResponse removeCartItem(Long userId, Long variantId) {
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);

        Integer qty = rawCart.remove(variantId);
        if (qty == null) {
            throw new ResourceNotFoundException("Item not found in cart: variantId=" + variantId);
        }

        // Release the reserved stock back to available pool
        reservationService.release(variantId, qty);
        saveRawCart(cartKey, rawCart);

        log.info("Cart remove: userId={} variantId={} qty={}", userId, variantId, qty);
        return buildCartResponse(rawCart);
    }

    /**
     * Clears all items from the cart and releases all inventory reservations.
     *
     * @param userId the authenticated user
     */
    @Transactional
    public void clearCart(Long userId) {
        String cartKey = cartKey(userId);
        Map<Long, Integer> rawCart = loadRawCart(cartKey);

        // Release all reservations before deleting the cart key
        rawCart.forEach((variantId, qty) -> {
            try {
                reservationService.release(variantId, qty);
            } catch (Exception e) {
                // Log but don't fail — cart should still be cleared even if a variant was deleted
                log.warn("Could not release reservation for variantId={} during clearCart: {}", variantId, e.getMessage());
            }
        });

        redisTemplate.delete(cartKey);
        log.info("Cart cleared: userId={}", userId);
    }

    /**
     * Merges an anonymous session cart into the authenticated user's cart on login.
     *
     * <p>For each item in the anonymous cart:
     * <ol>
     *   <li>If not in user cart — add it (subject to stock check)</li>
     *   <li>If already in user cart — take the max quantity (don't double-count)</li>
     * </ol>
     * The anonymous cart key is deleted after merging.
     * Any variant that fails stock validation is silently skipped (best-effort merge).
     *
     * @param sessionId the anonymous session identifier
     * @param userId    the now-authenticated user's ID
     */
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
            int mergedQty = Math.max(userQty, anonQty);   // take the larger quantity
            int delta = mergedQty - userQty;               // how many extra to reserve

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

    /**
     * Loads the raw cart from Redis as a map of variantId → quantity.
     * Returns an empty map if the key does not exist.
     */
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

    /**
     * Persists the cart map to Redis as a hash and refreshes the TTL.
     * If the map is empty, the key is deleted to avoid orphan entries.
     */
    private void saveRawCart(String cartKey, Map<Long, Integer> cart) {
        if (cart.isEmpty()) {
            redisTemplate.delete(cartKey);
            return;
        }
        // Overwrite entire hash: delete old + set all fields atomically via pipeline
        redisTemplate.delete(cartKey);
        Map<String, String> toStore = new HashMap<>();
        cart.forEach((k, v) -> toStore.put(k.toString(), v.toString()));
        redisTemplate.opsForHash().putAll(cartKey, toStore);
        redisTemplate.expire(cartKey, Duration.ofDays(cartTtlDays));
    }

    // ── Response builder ──────────────────────────────────────────────────────

    /**
     * Builds a full {@link CartResponse} from a raw variantId→quantity map.
     *
     * <p><b>N+1 prevention:</b> all variants are fetched in a single
     * {@code findAllById} batch call. Missing variants (deleted from catalog)
     * are silently dropped.
     */
    private CartResponse buildCartResponse(Map<Long, Integer> rawCart) {
        if (rawCart.isEmpty()) {
            return CartResponse.builder()
                    .items(List.of())
                    .itemCount(0)
                    .subtotal(BigDecimal.ZERO)
                    .build();
        }

        // Batch fetch all variants — single DB query
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

    /** Resolves the primary image URL from variant images or falls back to product gallery. */
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

    /**
     * Builds a human-readable variant label from attribute values.
     * Example: "Black / M" from Color=Black, Size=M.
     */
    private String resolveVariantLabel(ProductVariant variant) {
        if (variant.getAttributeValues() == null || variant.getAttributeValues().isEmpty()) {
            return variant.getSku();
        }
        return variant.getAttributeValues().stream()
                .map(AttributeValue::getValue)
                .collect(Collectors.joining(" / "));
    }

    // ── Key builders ──────────────────────────────────────────────────────────

    private String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    private String anonCartKey(String sessionId) {
        return ANON_CART_KEY_PREFIX + sessionId;
    }
}
