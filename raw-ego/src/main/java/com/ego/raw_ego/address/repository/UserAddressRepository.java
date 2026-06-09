package com.ego.raw_ego.address.repository;

import com.ego.raw_ego.address.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link UserAddress} entities.
 */
@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    /** All active addresses for a user, ordered by default-first then creation order. */
    @Query("""
        SELECT a FROM UserAddress a
        WHERE a.userId = :userId AND a.isActive = true
        ORDER BY a.isDefault DESC, a.createdAt ASC
        """)
    List<UserAddress> findActiveByUserId(@Param("userId") Long userId);

    /** Count of active (non-deleted) addresses for a user. */
    long countByUserIdAndIsActiveTrue(Long userId);

    /** Find the current default address for a user. */
    Optional<UserAddress> findByUserIdAndIsDefaultTrueAndIsActiveTrue(Long userId);

    /** Find a specific active address by id + userId (ownership check). */
    Optional<UserAddress> findByIdAndUserIdAndIsActiveTrue(Long id, Long userId);

    /**
     * Clears the default flag on ALL active addresses for a user.
     * Called before setting a new default to ensure only one is marked.
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE UserAddress a SET a.isDefault = false
        WHERE a.userId = :userId AND a.isActive = true
        """)
    void clearDefaultForUser(@Param("userId") Long userId);
}
