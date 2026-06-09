package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /** SKU uniqueness check used before persisting a new variant. */
    boolean existsBySku(String sku);

    /** Fetch variant by SKU — used in cart and order item resolution. */
    Optional<ProductVariant> findBySku(String sku);

    /**
     * Fetch all active variants for a product WITH their attribute values loaded.
     * JOIN FETCH prevents N+1 when building the variant matrix on the product detail page.
     */
    @Query("""
        SELECT DISTINCT v FROM ProductVariant v
        JOIN FETCH v.attributeValues av
        JOIN FETCH av.attributeType
        WHERE v.product.id = :productId
          AND v.active = true
        """)
    List<ProductVariant> findActiveVariantsWithAttributesByProductId(Long productId);

    /**
     * All variants (active and inactive) for admin product management.
     */
    @Query("SELECT v FROM ProductVariant v WHERE v.product.id = :productId ORDER BY v.createdAt ASC")
    List<ProductVariant> findAllByProductId(Long productId);

    /**
     * Count active variants with available stock — used to determine if a product
     * should transition from OUT_OF_STOCK → ACTIVE or ACTIVE → OUT_OF_STOCK.
     */
    @Query("""
        SELECT COUNT(v) FROM ProductVariant v
        JOIN v.inventoryRecord ir
        WHERE v.product.id = :productId
          AND v.active = true
          AND ir.quantityAvailable > 0
        """)
    long countActiveVariantsWithStock(Long productId);
}
