package com.ego.raw_ego.catalog.controller;

import com.ego.raw_ego.catalog.dto.request.AddHierarchyLinkRequest;
import com.ego.raw_ego.catalog.dto.request.CreateCategoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateCategoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateHierarchyLinkRequest;
import com.ego.raw_ego.catalog.dto.response.*;
import com.ego.raw_ego.catalog.service.CategoryService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Category REST controller — public storefront and admin management endpoints.
 *
 * <p><b>Public endpoints (no auth required):</b>
 * <pre>
 *   GET  /api/v1/categories                           → 3-level navigation tree (ROOT→GROUP→LEAF)
 *   GET  /api/v1/categories/leaves                    → flat list of all active LEAF categories
 *   GET  /api/v1/categories/{slug}                    → single category (with level, parent)
 *   GET  /api/v1/categories/{slug}/breadcrumbs        → canonical breadcrumb path (up to 3 items)
 * </pre>
 *
 * <p><b>Admin endpoints (ROLE_ADMIN):</b>
 * <pre>
 *   GET    /api/v1/admin/categories                               → all categories (flat, with level)
 *   POST   /api/v1/admin/categories                               → create ROOT, GROUP, or LEAF
 *   DELETE /api/v1/admin/categories/{id}                          → soft-deactivate
 *
 *   GET    /api/v1/admin/categories/{childId}/parents             → list parent links
 *   POST   /api/v1/admin/categories/{childId}/parents             → add a parent (cross-list)
 *   PATCH  /api/v1/admin/categories/{childId}/parents/{parentId}  → update link metadata
 *   DELETE /api/v1/admin/categories/{childId}/parents/{parentId}  → remove parent link
 *   PUT    /api/v1/admin/categories/{childId}/parents/{parentId}/primary → set as canonical
 *
 *   GET    /api/v1/admin/categories/{parentId}/children           → list child links
 *   PUT    /api/v1/admin/categories/{parentId}/children/reorder   → reorder children
 * </pre>
 */
@RestController
@Tag(name = "Categories", description = "3-level category taxonomy (ROOT→GROUP→LEAF), navigation, and admin management")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // ── Public: storefront ───────────────────────────────────────────────────

    @GetMapping("/api/v1/categories")
    @Operation(
        summary = "Get 3-level navigation tree",
        description = "Returns all active ROOT categories, each with GROUP subcategories, " +
                      "each GROUP carrying its LEAF subcategories. Powers the mega-menu. " +
                      "Items are sorted by per-parent display order from the hierarchy link table."
    )
    public ResponseEntity<ApiResponse<List<CategoryTreeResponse>>> getNavigationTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getNavigationTree()));
    }

    @GetMapping("/api/v1/categories/leaves")
    @Operation(
        summary = "Get all leaf categories",
        description = "Returns a flat list of all active LEAF categories (depth=2). " +
                      "Used by the admin product creation form to populate the category picker. " +
                      "Products may ONLY be assigned to LEAF categories."
    )
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getLeafCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllLeafCategories()));
    }


    @GetMapping("/api/v1/categories/{slug}")
    @Operation(
        summary = "Get category by slug",
        description = "Returns a single active category by URL slug. " +
                      "For subcategories, the response includes a 'parents' array listing " +
                      "all root categories this subcategory belongs to."
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryBySlug(slug)));
    }

    @GetMapping("/api/v1/categories/{slug}/breadcrumbs")
    @Operation(
        summary = "Get canonical breadcrumb path",
        description = "Returns the ordered breadcrumb trail for a category. " +
                      "Always uses the canonical primary parent — provides a single " +
                      "deterministic path regardless of multi-parent cross-listings. " +
                      "Root: [root]. Subcategory: [canonicalRoot, subcategory]."
    )
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryBreadcrumbs(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryBreadcrumbs(slug)));
    }

    // ── Admin: category CRUD ─────────────────────────────────────────────────

    @GetMapping("/api/v1/admin/categories")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] List all categories",
        description = "Returns all categories (active and inactive) for admin management."
    )
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategoriesAdmin() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategoriesAdmin()));
    }

    @PostMapping("/api/v1/admin/categories")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Create category",
        description = "Creates a root category (parentIds null/empty) or subcategory (parentIds=[...rootIds]). " +
                      "First entry in parentIds becomes the canonical parent_id. " +
                      "Subsequent entries create additional hierarchy links for cross-listing. " +
                      "Max depth = 1. All parentIds must reference active root categories."
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully.", response));
    }

    @PatchMapping("/api/v1/admin/categories/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Update category details",
        description = "Updates a category's name, description, imageUrl, displayOrder, and optionally slug. " +
                      "Category code is NOT editable (immutable — embedded in all variant SKUs). " +
                      "If name changes and no explicit slug is provided, slug is auto-regenerated. " +
                      "WARNING: slug change breaks any existing bookmarked or cached URLs to the old slug."
    )
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully.", response));
    }

    @DeleteMapping("/api/v1/admin/categories/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Deactivate category",
        description = "Soft-deactivates a category. Does not delete — preserves product associations " +
                      "and hierarchy links. Deactivated categories are automatically hidden from storefront."
    )
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(@PathVariable Long id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deactivated."));
    }

    @PatchMapping("/api/v1/admin/categories/{id}/activate")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Re-activate a deactivated category",
        description = "Restores a soft-deactivated category to active status. " +
                      "The category will immediately reappear in the storefront navigation tree " +
                      "and product listings. Returns 409 Conflict if the category is already active."
    )
    public ResponseEntity<ApiResponse<Void>> activateCategory(@PathVariable Long id) {
        categoryService.activateCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category re-activated."));
    }

    @DeleteMapping("/api/v1/admin/categories/{id}/permanent")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Permanently delete category",
        description = """
            Permanently removes a category row from the database. This action is IRREVERSIBLE.

            Pre-conditions (all must hold before this call will succeed):
            1. The category must already be soft-deactivated (call DELETE /admin/categories/{id} first).
            2. No products (any status) may be assigned to this category.
            3. If this is a root category, it must have no subcategories.

            The endpoint automatically purges all CategoryHierarchyLink rows where
            this category appears as parent or child before removing the category itself.

            Returns 409 Conflict if any pre-condition is not met, with a descriptive message.
            """
    )
    public ResponseEntity<ApiResponse<Void>> hardDeleteCategory(@PathVariable Long id) {
        categoryService.hardDeleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category permanently deleted."));
    }

    // ── Admin: hierarchy link management ─────────────────────────────────────

    @GetMapping("/api/v1/admin/categories/{childId}/parents")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] List parent links for a subcategory",
        description = "Returns all hierarchy links (parent assignments) for a subcategory. " +
                      "Includes link metadata: isPrimary, displayOrder, isVisible, navigationLabel."
    )
    public ResponseEntity<ApiResponse<List<CategoryHierarchyLinkResponse>>> getParentLinks(
            @PathVariable Long childId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getLinksForChild(childId)));
    }

    @PostMapping("/api/v1/admin/categories/{childId}/parents")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Add a parent to a subcategory",
        description = "Cross-lists a subcategory under an additional root category. " +
                      "Idempotent: if the link already exists it is returned unchanged. " +
                      "Example: POST to childId=5 (Hoodies) with parentId=2 (Women) " +
                      "makes Hoodies appear under both Men and Women in the navigation tree."
    )
    public ResponseEntity<ApiResponse<CategoryHierarchyLinkResponse>> addParentToCategory(
            @PathVariable Long childId,
            @Valid @RequestBody AddHierarchyLinkRequest request) {
        CategoryHierarchyLinkResponse response = categoryService.addParentToCategory(childId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Parent added to category.", response));
    }

    @PatchMapping("/api/v1/admin/categories/{childId}/parents/{parentId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Update hierarchy link metadata",
        description = "Updates display order, visibility, or navigation label on a specific parent link. " +
                      "All fields are optional — only supplied fields are updated."
    )
    public ResponseEntity<ApiResponse<CategoryHierarchyLinkResponse>> updateHierarchyLink(
            @PathVariable Long childId,
            @PathVariable Long parentId,
            @Valid @RequestBody UpdateHierarchyLinkRequest request) {
        CategoryHierarchyLinkResponse response =
                categoryService.updateHierarchyLink(childId, parentId, request);
        return ResponseEntity.ok(ApiResponse.success("Hierarchy link updated.", response));
    }

    @DeleteMapping("/api/v1/admin/categories/{childId}/parents/{parentId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Remove a parent from a subcategory",
        description = "Removes a cross-listing. The subcategory will no longer appear under this " +
                      "parent's navigation section. Blocked if it would orphan the subcategory " +
                      "(i.e. this is the last remaining parent link)."
    )
    public ResponseEntity<ApiResponse<Void>> removeParentFromCategory(
            @PathVariable Long childId,
            @PathVariable Long parentId) {
        categoryService.removeParentFromCategory(childId, parentId);
        return ResponseEntity.ok(ApiResponse.success("Parent removed from category."));
    }

    @PutMapping("/api/v1/admin/categories/{childId}/parents/{parentId}/primary")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Set canonical primary parent",
        description = "Promotes a specific parent link to the canonical primary parent. " +
                      "This updates both the hierarchy link isPrimary flag AND the " +
                      "categories.parent_id FK column, keeping both systems in sync. " +
                      "Affects canonical breadcrumbs and SKU segment. Use with care."
    )
    public ResponseEntity<ApiResponse<CategoryHierarchyLinkResponse>> setPrimaryParent(
            @PathVariable Long childId,
            @PathVariable Long parentId) {
        CategoryHierarchyLinkResponse response = categoryService.setPrimaryParent(childId, parentId);
        return ResponseEntity.ok(ApiResponse.success("Primary parent updated.", response));
    }

    @GetMapping("/api/v1/admin/categories/{parentId}/children")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] List child links for a root category",
        description = "Returns all hierarchy links (child assignments) under a root category " +
                      "including hidden/invisible links. Used for admin mega-menu configuration."
    )
    public ResponseEntity<ApiResponse<List<CategoryHierarchyLinkResponse>>> getChildLinks(
            @PathVariable Long parentId) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getLinksForParent(parentId)));
    }

    @PutMapping("/api/v1/admin/categories/{parentId}/children/reorder")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Reorder children under a root category",
        description = "Accepts an ordered list of child category IDs and applies " +
                      "sequential displayOrder values (0, 1, 2...) to their hierarchy links " +
                      "under this parent. Only affects ordering within this parent's context."
    )
    public ResponseEntity<ApiResponse<List<CategoryHierarchyLinkResponse>>> reorderChildren(
            @PathVariable Long parentId,
            @RequestBody List<Long> orderedChildIds) {
        List<CategoryHierarchyLinkResponse> response =
                categoryService.reorderCategoryChildren(parentId, orderedChildIds);
        return ResponseEntity.ok(ApiResponse.success("Children reordered.", response));
    }
}
