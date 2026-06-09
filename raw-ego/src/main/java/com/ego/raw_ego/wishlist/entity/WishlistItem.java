package com.ego.raw_ego.wishlist.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * JPA entity mapping to the {@code wishlist_items} table.
 *
 * <p>A wishlist item represents a customer's intent to purchase a specific
 * {@link com.ego.raw_ego.catalog.entity.ProductVariant}. Items are keyed by
 * {@code variant_id} (not product ID) — the customer wishes for a specific color/size.
 *
 * <p><b>Uniqueness:</b> A user may save each variant only once.
 * The {@code uq_wishlist_user_variant} constraint makes add idempotent at the DB level;
 * the service layer checks proactively to return a clean 200 OK (not 409).
 *
 * <p><b>Storage:</b> MySQL (not Redis) — wishlists are durable across sessions.
 */
@Entity
@Table(
        name = "wishlist_items",
        indexes = {
                @Index(name = "idx_wishlist_user", columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_wishlist_user_variant",
                        columnNames = {"user_id", "variant_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Raw FK to {@code users.id}.
     * Not an entity reference — wishlist module stays decoupled from auth module.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Raw FK to {@code product_variants.id}.
     * Not an entity reference — wishlist module stays decoupled from catalog module.
     */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
