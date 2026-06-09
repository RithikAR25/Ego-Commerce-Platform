package com.ego.raw_ego.catalog.dto.response;

import com.ego.raw_ego.catalog.entity.Category;
import com.ego.raw_ego.catalog.entity.CategoryHierarchyLink;
import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Navigation tree response: one ROOT category with its GROUP and LEAF subcategories.
 *
 * <p>3-level structure:
 * <pre>
 * ROOT (this object)
 *   └── groups: List&lt;CategoryTreeItemResponse&gt;       ← GROUP entries
 *         └── leafCategories: List&lt;CategoryTreeItemResponse&gt;  ← LEAF entries
 * </pre>
 *
 * <p>Each group and leaf entry carries link-level metadata (display order per parent,
 * navigation label override, visibility flag, primary flag) sourced from
 * {@link CategoryHierarchyLink}, not from the category's own global fields.
 */
@Data
@Builder
public class CategoryTreeResponse {

    private Long    id;
    private String  name;
    private String  code;
    private String  slug;
    private String  imageUrl;
    private Integer displayOrder;

    /**
     * Ordered list of GROUP categories under this ROOT.
     * Each group carries its own {@code leafCategories} list.
     *
     * <p>Renamed from {@code subcategories} (clean-slate architecture — no legacy consumers).
     */
    private List<CategoryTreeItemResponse> groups;

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Builds the 3-level tree for a single ROOT from its pre-fetched visible hierarchy links.
     *
     * <p>The {@code links} list may contain both GROUP-level links (parent=ROOT) and
     * LEAF-level links (parent=GROUP). This factory groups them accordingly.
     *
     * @param root  the ROOT category entity
     * @param links visible links visible within the entire subtree of this ROOT
     */
    public static CategoryTreeResponse fromLinks(Category root, List<CategoryHierarchyLink> links) {
        if (links == null || links.isEmpty()) {
            return CategoryTreeResponse.builder()
                    .id(root.getId())
                    .name(root.getName())
                    .code(root.getCode())
                    .slug(root.getSlug())
                    .imageUrl(root.getImageUrl())
                    .displayOrder(root.getDisplayOrder())
                    .groups(new ArrayList<>())
                    .build();
        }

        // Partition: GROUP links (parent == root) vs LEAF links (parent is GROUP, not ROOT)
        List<CategoryHierarchyLink> groupLinks = new ArrayList<>();
        Map<Long, List<CategoryHierarchyLink>> leafLinksByGroupId = new LinkedHashMap<>();

        for (CategoryHierarchyLink link : links) {
            Category parent = link.getParent();
            if (parent.getId().equals(root.getId())) {
                // This is a ROOT→GROUP link
                groupLinks.add(link);
            } else {
                // This is a GROUP→LEAF link — parent is a GROUP
                leafLinksByGroupId
                        .computeIfAbsent(parent.getId(), k -> new ArrayList<>())
                        .add(link);
            }
        }

        // Sort group links by their link-level displayOrder
        groupLinks.sort(Comparator.comparingInt(CategoryHierarchyLink::getDisplayOrder));

        // Build group items, each carrying their leaf children
        List<CategoryTreeItemResponse> groupItems = groupLinks.stream()
                .map(groupLink -> {
                    Long groupId = groupLink.getChild().getId();
                    List<CategoryHierarchyLink> leafLinks =
                            leafLinksByGroupId.getOrDefault(groupId, List.of());

                    List<CategoryTreeItemResponse> leafItems = leafLinks.stream()
                            .sorted(Comparator.comparingInt(CategoryHierarchyLink::getDisplayOrder))
                            .map(CategoryTreeItemResponse::from)
                            .collect(Collectors.toList());

                    return CategoryTreeItemResponse.fromGroup(groupLink, leafItems);
                })
                .collect(Collectors.toList());

        return CategoryTreeResponse.builder()
                .id(root.getId())
                .name(root.getName())
                .code(root.getCode())
                .slug(root.getSlug())
                .imageUrl(root.getImageUrl())
                .displayOrder(root.getDisplayOrder())
                .groups(groupItems)
                .build();
    }
}
