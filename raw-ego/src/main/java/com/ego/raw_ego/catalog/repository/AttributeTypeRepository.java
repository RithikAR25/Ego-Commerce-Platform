package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.AttributeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttributeTypeRepository extends JpaRepository<AttributeType, Long> {

    /** All attribute types for a product, ordered by display_order ASC. */
    List<AttributeType> findByProductIdOrderByDisplayOrderAsc(Long productId);

    /** Duplicate-name guard within a product scope. */
    Optional<AttributeType> findByProductIdAndNameIgnoreCase(Long productId, String name);
}
