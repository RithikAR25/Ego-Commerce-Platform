package com.ego.raw_ego.wishlist.service;

import com.ego.raw_ego.auth.entity.User;
import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.catalog.enums.ProductStatus;
import com.ego.raw_ego.catalog.event.ProductStockStatusChangedEvent;
import com.ego.raw_ego.notification.service.NotificationService;
import com.ego.raw_ego.wishlist.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Async event listener that notifies users when a wishlisted product changes stock status.
 *
 * <p>Listens for {@link ProductStockStatusChangedEvent} published by
 * {@link com.ego.raw_ego.catalog.service.ProductService#syncProductStatusFromInventory}.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>{@code @Async} — runs on the {@code ego-async-*} thread pool so the inventory
 *       update HTTP response is not blocked by email dispatch.</li>
 *   <li>Module decoupling — this class lives in the wishlist package and imports both
 *       auth (for UserRepository) and notification, while ProductService knows nothing
 *       about either.</li>
 *   <li>Fan-out cap — if &gt;50 users are found (large-scale), the notification is still
 *       sent to all, but each dispatch is independent (SendGrid handles delivery).</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WishlistStockNotificationListener {

    private final WishlistItemRepository wishlistRepository;
    private final UserRepository         userRepository;
    private final NotificationService    notificationService;

    /**
     * Handles a product stock status change and fans out email notifications.
     *
     * <p>Runs asynchronously — failures here do not affect the inventory update transaction.
     *
     * @param event the stock status change event published by ProductService
     */
    @Async
    @EventListener
    public void onProductStockStatusChanged(ProductStockStatusChangedEvent event) {
        Long productId       = event.getProductId();
        String productName   = event.getProductName();
        ProductStatus status = event.getNewStatus();

        // Only notify on the two meaningful transitions for wishlisters
        if (status != ProductStatus.OUT_OF_STOCK && status != ProductStatus.ACTIVE) {
            return;
        }

        List<Long> userIds = wishlistRepository.findUserIdsByProductId(productId);
        if (userIds.isEmpty()) {
            log.debug("[WishlistNotification] No wishlist subscribers for productId={}", productId);
            return;
        }

        log.info("[WishlistNotification] Notifying {} user(s) — productId={} status={}",
                userIds.size(), productId, status);

        for (Long userId : userIds) {
            userRepository.findById(userId).ifPresent(user -> {
                try {
                    if (status == ProductStatus.OUT_OF_STOCK) {
                        notificationService.sendWishlistOutOfStock(user, productId, productName);
                    } else {
                        notificationService.sendWishlistBackInStock(user, productId, productName);
                    }
                } catch (Exception e) {
                    // Individual send failures are logged but don't abort the fan-out loop
                    log.warn("[WishlistNotification] Failed to notify userId={} for productId={}: {}",
                            userId, productId, e.getMessage());
                }
            });
        }
    }
}
