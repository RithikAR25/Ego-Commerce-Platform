package com.ego.raw_ego.catalog.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines an attribute axis for a product — e.g. "Color" or "Size".
 *
 * <p>Each Product has 2 attribute types: Color and Size.
 * Their possible values are stored in {@link AttributeValue}.
 * The cross-product of (Color × Size) forms the variant matrix.
 *
 * <p>Why EAV instead of hardcoded columns?
 * Adding a new attribute axis (e.g. "Fit", "Material") requires only new rows,
 * not a schema ALTER TABLE. The frontend reads attribute structure dynamically.
 */
@Entity
@Table(
    name = "product_attribute_types",
    indexes = {
        @Index(name = "idx_attr_type_product", columnList = "product_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttributeType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Display name, e.g. "Color" or "Size". */
    @Column(nullable = false, length = 50)
    private String name;

    /** Controls display order in the variant selector. Lower = first. */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "attributeType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AttributeValue> values = new ArrayList<>();
}
