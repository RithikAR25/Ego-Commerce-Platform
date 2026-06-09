package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A specific value for an attribute axis — e.g. "Black" under "Color", or "M" under "Size".
 *
 * <p><b>SKU role:</b> The {@code code} field is used directly in SKU generation:
 * <ul>
 *   <li>Color value: code="BLK" → EGO-TEE-0001-<b>BLK</b>-M</li>
 *   <li>Size value:  code="M"   → EGO-TEE-0001-BLK-<b>M</b></li>
 * </ul>
 * Codes must be short, uppercase, and stable — they become part of the immutable SKU.
 *
 * <p><b>Swatch support:</b>
 * {@code hexColor} supports color swatches in the UI (e.g. "#1A1A1A" for Black).
 * {@code swatchImageUrl} supports pattern/texture swatches (e.g. camouflage print).
 */
@Entity
@Table(
    name = "product_attribute_values",
    indexes = {
        @Index(name = "idx_attr_value_type", columnList = "attribute_type_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_type_id", nullable = false)
    private AttributeType attributeType;

    /** Display label shown to the customer, e.g. "Olive Green", "Extra Large". */
    @Column(nullable = false, length = 100)
    private String value;

    /**
     * Short uppercase code used in SKU generation.
     * Must be unique within a product's attribute type.
     * Color examples: BLK, WHT, RED, OLV, NVY, GRY
     * Size examples:  XS, S, M, L, XL, XXL
     */
    @Column(nullable = false, length = 10)
    private String code;

    /** Controls display order in the variant selector. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    /**
     * CSS hex color code for the color swatch UI — null for size attributes.
     * Example: "#1A1A1A" for Black, "#FFFFFF" for White.
     */
    @Column(name = "hex_color", length = 7)
    private String hexColor;

    /**
     * Cloudinary URL for pattern/texture swatches.
     * Used when a plain hex color is insufficient (e.g. camouflage, tie-dye).
     * Null for size attributes and solid color attributes.
     */
    @Column(name = "swatch_image_url", length = 500)
    private String swatchImageUrl;
}
