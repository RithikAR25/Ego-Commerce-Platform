package com.ego.raw_ego.catalog.repository;

import com.ego.raw_ego.catalog.entity.VariantImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VariantImageRepository extends JpaRepository<VariantImage, Long> {

    List<VariantImage> findByVariantIdOrderByDisplayOrderAsc(Long variantId);

    Optional<VariantImage> findByVariantIdAndPrimaryTrue(Long variantId);

    /** Used to clean up Cloudinary resources when a variant is deleted. */
    @Query("SELECT vi.cloudinaryPublicId FROM VariantImage vi WHERE vi.variant.id = :variantId")
    List<String> findCloudinaryPublicIdsByVariantId(Long variantId);

    @Modifying
    @Query("UPDATE VariantImage vi SET vi.primary = false WHERE vi.variant.id = :variantId")
    int clearPrimaryFlagForVariant(Long variantId);

    @Modifying
    @Query("UPDATE VariantImage vi SET vi.displayOrder = :order WHERE vi.id = :id")
    int updateDisplayOrder(Long id, int order);
}
