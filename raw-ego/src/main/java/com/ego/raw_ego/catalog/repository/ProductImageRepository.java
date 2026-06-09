package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(Long productId);

    /** Used to clean up Cloudinary orphans on product delete. */
    @Query("SELECT pi.cloudinaryPublicId FROM ProductImage pi WHERE pi.product.id = :productId")
    List<String> findCloudinaryPublicIdsByProductId(Long productId);

    @Modifying
    @Query("UPDATE ProductImage pi SET pi.displayOrder = :order WHERE pi.id = :id")
    int updateDisplayOrder(Long id, int order);
}
