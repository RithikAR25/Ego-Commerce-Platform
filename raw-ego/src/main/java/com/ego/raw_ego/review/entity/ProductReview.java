package com.ego.raw_ego.review.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * JPA entity mapping to the {@code product_reviews} table.
 *
 * <p><b>Purchase gate:</b> Users may only submit a review if they have at least
 * one {@code DELIVERED} order containing a variant of the reviewed product.
 * This is enforced in {@link com.ego.raw_ego.review.service.ReviewService}.
 *
 * <p><b>One review per user per product</b> — enforced via the
 * {@code uq_user_product_review} unique constraint. Attempting to submit
 * a second review throws a {@link org.springframework.dao.DataIntegrityViolationException}
 * which GlobalExceptionHandler maps to {@code 409 Conflict}.
 *
 * <p><b>Moderation:</b> Auto-approved (no moderation queue). Admin may hard-delete
 * any review via {@code DELETE /api/v1/admin/reviews/{id}}.
 */
@Entity
@Table(
        name = "product_reviews",
        indexes = {
                @Index(name = "idx_review_product", columnList = "product_id"),
                @Index(name = "idx_review_user",    columnList = "user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_product_review",
                        columnNames = {"user_id", "product_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Raw FK to {@code products.id}.
     * Not an entity reference — avoids cross-module Hibernate coupling.
     */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * Raw FK to {@code users.id}.
     * Not an entity reference — avoids cross-module Hibernate coupling.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Reviewer's first name — denormalized at write time for display performance. */
    @Column(name = "reviewer_first_name", nullable = false, length = 100)
    private String reviewerFirstName;

    /** Rating from 1 (worst) to 5 (best). */
    @Column(nullable = false)
    private int rating;

    /** Short headline for the review (optional). */
    @Column(length = 150)
    private String title;

    /** Full review body (optional). */
    @Column(columnDefinition = "TEXT")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
