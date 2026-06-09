package com.ego.raw_ego.auth.repository;

import com.ego.raw_ego.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Data access layer for the {@code users} table.
 *
 * <p>All finder methods include {@code isDeletedFalse} to enforce soft-delete
 * transparency — deleted users are invisible to all business logic.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Load a non-deleted user by email. Used by login and UserDetailsService.
     */
    Optional<User> findByEmailAndDeletedFalse(String email);

    /**
     * Quick existence check before registration — avoids loading the full entity.
     */
    boolean existsByEmailAndDeletedFalse(String email);

    /**
     * Updates last_login_at without bumping the @Version optimistic lock.
     * Using @Modifying + JPQL bypasses the entity merge to avoid unnecessary
     * dirty-checking on the entire user row.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :lastLoginAt WHERE u.id = :userId")
    void updateLastLoginAt(@Param("userId") Long userId,
                           @Param("lastLoginAt") Instant lastLoginAt);

    // ── Admin user management queries ────────────────────────────────────────────

    /**
     * Admin: all non-deleted users, newest first.
     * Used by {@code GET /api/v1/admin/users} when no search term is provided.
     *
     * <p>Spring Data derives: {@code SELECT u FROM User u WHERE u.deleted = false ORDER BY u.createdAt DESC}.
     */
    Page<User> findByDeletedFalseOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Admin: find a non-deleted user by ID.
     * Returns empty if the user does not exist or has been soft-deleted.
     * Used by {@code GET /api/v1/admin/users/{id}}.
     *
     * <p>Spring Data derives: {@code WHERE u.id = :id AND u.deleted = false}.
     */
    Optional<User> findByIdAndDeletedFalse(Long id);

    /**
     * Admin: search non-deleted users by email, first name, or last name (case-insensitive).
     * Used by {@code GET /api/v1/admin/users?search=...}.
     *
     * <p>Uses {@code LIKE '%term%'} with LOWER() for case-insensitive matching.
     * For production scale, consider a full-text index on (email, first_name, last_name).
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deleted = false
              AND (LOWER(u.email)     LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :term, '%')))
            ORDER BY u.createdAt DESC
            """)
    Page<User> searchByTerm(@Param("term") String term, Pageable pageable);
}
