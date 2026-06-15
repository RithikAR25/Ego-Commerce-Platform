package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.AddHierarchyLinkRequest;
import com.ego.raw_ego.catalog.dto.request.CreateCategoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateCategoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateHierarchyLinkRequest;
import com.ego.raw_ego.catalog.dto.response.*;
import com.ego.raw_ego.catalog.entity.Category;

import java.util.List;

/**
 * Contract for category management in the 3-level enterprise taxonomy.
 *
 * <p><b>Depth model:</b>
 * <ul>
 *   <li>ROOT  (depth=0) — "MEN", "WOMEN", "KIDS". Navigation top-level.</li>
 *   <li>GROUP (depth=1) — "Topwear", "Bottomwear". Navigation column headers.</li>
 *   <li>LEAF  (depth=2) — "T-Shirts", "Jeans". Product assignment targets.</li>
 * </ul>
 * Max depth = 2. Products MUST be assigned to LEAF categories only.
 */
public interface CategoryService {

    List<CategoryTreeResponse> getNavigationTree();

    List<CategoryResponse> getAllLeafCategories();

    CategoryResponse getCategoryBySlug(String slug);

    List<CategoryResponse> getCategoryBreadcrumbs(String slug);

    List<CategoryResponse> getAllCategoriesAdmin();

    List<CategoryHierarchyLinkResponse> getLinksForParent(Long parentId);

    List<CategoryHierarchyLinkResponse> getLinksForChild(Long childId);

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryHierarchyLinkResponse addParentToCategory(Long childId, AddHierarchyLinkRequest request);

    void removeParentFromCategory(Long childId, Long parentId);

    CategoryHierarchyLinkResponse updateHierarchyLink(Long childId, Long parentId, UpdateHierarchyLinkRequest request);

    CategoryHierarchyLinkResponse setPrimaryParent(Long childId, Long parentId);

    List<CategoryHierarchyLinkResponse> reorderCategoryChildren(Long parentId, List<Long> orderedChildIds);

    CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);

    void deactivateCategory(Long id);

    void activateCategory(Long id);

    void hardDeleteCategory(Long id);

    /** Used by ProductService to resolve category by ID. */
    Category findById(Long id);
}
