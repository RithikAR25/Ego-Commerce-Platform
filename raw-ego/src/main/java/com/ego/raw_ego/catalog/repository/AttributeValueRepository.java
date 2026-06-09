package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.AttributeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttributeValueRepository extends JpaRepository<AttributeValue, Long> {

    List<AttributeValue> findByAttributeTypeId(Long attributeTypeId);

    List<AttributeValue> findByAttributeTypeProductIdOrderByDisplayOrderAsc(Long productId);
}
