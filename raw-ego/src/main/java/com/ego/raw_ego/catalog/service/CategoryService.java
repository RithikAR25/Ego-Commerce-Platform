package com.ego.raw_ego.catalog.service;

import com.ego.raw_ego.catalog.dto.request.AddHierarchyLinkRequest;
import com.ego.raw_ego.catalog.dto.request.CreateCategoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateCategoryRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateHierarchyLinkRequest;
import com.ego.raw_ego.catalog.dto.response.*;
import com.ego.raw_ego.catalog.entity.Category;
import com.ego.raw_ego.catalog.entity.CategoryHierarchyLink;
import com.ego.raw_ego.catalog.repository.CategoryHierarchyLinkRepository;
import com.ego.raw_ego.catalog.repository.CategoryRepository;
import com.ego.raw_ego.catalog.repository.ProductRepository;
import com.ego.raw_ego.common.exception.ConflictException;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import com.ego.raw_ego.common.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Business logic for category management in the 3-level enterprise taxonomy.
 *
 * <p><b>Depth model (clean-slate enterprise architecture):</b>
 * <ul>
 *   <li>ROOT  (depth=0) — "MEN", "WOMEN", "KIDS". Navigation top-level.</li>
 *   <li>GROUP (depth=1) — "Topwear", "Bottomwear". Navigation column headers.</li>
 *   <li>LEAF  (depth=2) — "T-Shirts", "Jeans". Product assignment targets.</li>
 * </ul>
 * Max depth = 2. Products MUST be assigned to LEAF categories only.
 *
 * <p><b>Dual-system design (retained):</b>
 * <ul>
 *   <li>{@code categories.parent_id} — canonical single-parent FK for breadcrumbs and SKUs.</li>
 *   <li>{@link CategoryHierarchyLink} — powers multi-parent cross-listing, ordering, visibility.</li>
 * </ul>
 * All hierarchy mutations keep both systems in sync transactionally.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository              categoryRepository;
    private final CategoryHierarchyLinkRepository linkRepository;
    private final ProductRepository               productRepository;

    // ── Public storefront ────────────────────────────────────────────────────

    /**
     * Returns the full 3-level navigation tree from the hierarchy link table.
     * Structure: ROOT → GROUP (with leafCategories) → LEAF.
     *
     * <p><b>Performance:</b> Single JOIN FETCH query across category_hierarchy_links,
     * then grouped in Java. No N+1, no eager graph explosion.
     *
     * <p>If no hierarchy links exist (fresh DB before seeding), returns an empty list.
     */
    @Transactional(readOnly = true)
    public List<CategoryTreeResponse> getNavigationTree() {
        List<CategoryHierarchyLink> links = linkRepository.findAllVisibleForNavigation();

        // No legacy fallback — if no links exist, return empty tree (seed data not loaded yet)
        if (links.isEmpty()) {
            log.warn("No hierarchy links found — returning empty navigation tree. Run category seed.");
            return List.of();
        }

        // Collect all unique roots encountered in the links (JOIN FETCHed)
        Map<Long, Category> rootsById = new LinkedHashMap<>();
        // links grouped by root ID
        Map<Long, List<CategoryHierarchyLink>> linksByRootId = new LinkedHashMap<>();

        for (CategoryHierarchyLink link : links) {
            Category parent = link.getParent();
            if (parent.isRoot()) {
                // ROOT→GROUP link
                rootsById.put(parent.getId(), parent);
                linksByRootId
                        .computeIfAbsent(parent.getId(), k -> new ArrayList<>())
                        .add(link);
            } else if (parent.isGroup()) {
                // GROUP→LEAF link: key by the group's canonical root
                Category root = parent.getParent();
                if (root != null) {
                    linksByRootId
                            .computeIfAbsent(root.getId(), k -> new ArrayList<>())
                            .add(link);
                }
            }
        }

        // Ensure all active roots appear even if they have no children yet
        List<Category> allActiveRoots = categoryRepository.findAllRootCategoriesActive();
        for (Category root : allActiveRoots) {
            rootsById.putIfAbsent(root.getId(), root);
        }

        // Build tree in root display-order; CategoryTreeResponse.fromLinks() partitions
        // ROOT→GROUP and GROUP→LEAF links internally using parent.isRoot()/isGroup().
        return rootsById.values().stream()
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .map(root -> CategoryTreeResponse.fromLinks(
                        root,
                        linksByRootId.getOrDefault(root.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * Returns all active LEAF categories in a flat list.
     * Used by admin product creation to populate the category picker.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllLeafCategories() {
        return categoryRepository.findAllLeafCategoriesActive()
                .stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Returns a single active category (ROOT, GROUP, or LEAF) by its URL slug.
     * For GROUP and LEAF categories, hierarchy links are loaded.
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));
        return CategoryResponse.from(category);
    }

    /**
     * Returns the canonical breadcrumb path for a category by traversing the parent chain.
     *
     * <p>Return format:
     * <ul>
     *   <li>ROOT  → {@code [root]}</li>
     *   <li>GROUP → {@code [root, group]}</li>
     *   <li>LEAF  → {@code [root, group, leaf]}</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoryBreadcrumbs(String slug) {
        Category category = categoryRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + slug));

        List<CategoryResponse> breadcrumbs = new ArrayList<>();
        Category current = category;
        while (current != null) {
            breadcrumbs.add(0, CategoryResponse.from(current));
            current = current.getParent();
        }
        return breadcrumbs;
    }

    // ── Admin: read ──────────────────────────────────────────────────────────

    /**
     * Returns all categories (active and inactive) for admin management,
     * enriched with a per-category product count.
     *
     * <p><b>Performance:</b> exactly 2 queries:
     * 1. {@code findAll()} on the categories table
     * 2. A single {@code GROUP BY} query for product counts
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategoriesAdmin() {
        List<Category> all = categoryRepository.findAll();

        Map<Long, Long> countById = productRepository.countProductsGroupedByCategoryId()
                .stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return all.stream()
                .map(cat -> {
                    CategoryResponse resp = CategoryResponse.from(cat);
                    resp.setProductCount(countById.getOrDefault(cat.getId(), 0L));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns all hierarchy links for a specific parent — used for admin
     * mega-menu configuration and child ordering UI.
     */
    @Transactional(readOnly = true)
    public List<CategoryHierarchyLinkResponse> getLinksForParent(Long parentId) {
        findCategoryById(parentId); // validates existence
        return linkRepository.findAllByParentId(parentId)
                .stream()
                .map(CategoryHierarchyLinkResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Returns all parent links for a specific non-root category.
     */
    @Transactional(readOnly = true)
    public List<CategoryHierarchyLinkResponse> getLinksForChild(Long childId) {
        findNonRootById(childId); // validates existence and that it is not ROOT
        return linkRepository.findAllByChildId(childId)
                .stream()
                .map(CategoryHierarchyLinkResponse::from)
                .collect(Collectors.toList());
    }

    // ── Admin: create ────────────────────────────────────────────────────────

    /**
     * Creates a new category.
     *
     * <p><b>ROOT category:</b> {@code parentIds} null or empty.
     * <p><b>GROUP category:</b> {@code parentIds} contains one or more ROOT IDs.
     * <p><b>LEAF category:</b> {@code parentIds} contains one or more GROUP IDs.
     *
     * <p><b>Depth rule:</b> max depth = 2. A LEAF cannot be a parent.
     */
    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        // Uniqueness check
        if (categoryRepository.existsByCode(request.getCode())) {
            throw new ConflictException("Category code '" + request.getCode() + "' is already in use.");
        }

        boolean hasParents = request.getParentIds() != null && !request.getParentIds().isEmpty();

        // Resolve and validate all parents
        List<Category> parents = new ArrayList<>();
        if (hasParents) {
            for (Long parentId : request.getParentIds()) {
                Category parent = categoryRepository.findById(parentId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Parent category not found: id=" + parentId));

                // Depth enforcement: LEAF categories cannot be parents
                if (parent.isLeaf()) {
                    throw new IllegalArgumentException(
                            "Cannot create a subcategory under a LEAF category '" + parent.getName() +
                            "'. LEAF categories are terminal nodes. Max depth is 2.");
                }
                // All parents must be at the same depth level (consistency check)
                if (!parents.isEmpty() && parent.getDepth() != parents.get(0).getDepth()) {
                    throw new IllegalArgumentException(
                            "All parent categories must be at the same depth level.");
                }
                if (!parent.isActive()) {
                    throw new IllegalArgumentException(
                            "Cannot link to inactive parent category '" + parent.getName() + "'.");
                }
                parents.add(parent);
            }
        }

        // Generate unique slug
        String slug = SlugUtils.toUniqueSlug(request.getName(), categoryRepository::existsBySlug);

        // Canonical parent = first in the list (or null for ROOT)
        Category canonicalParent = parents.isEmpty() ? null : parents.get(0);

        Category category = Category.builder()
                .name(request.getName().trim())
                .code(request.getCode().toUpperCase())
                .slug(slug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .parent(canonicalParent)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .active(true)
                .build();

        category = categoryRepository.save(category);

        // Create hierarchy links for each parent
        if (!parents.isEmpty()) {
            boolean isFirst = true;
            for (Category parent : parents) {
                CategoryHierarchyLink link = CategoryHierarchyLink.builder()
                        .parent(parent)
                        .child(category)
                        .primary(isFirst)
                        .displayOrder(category.getDisplayOrder())
                        .visible(true)
                        .build();
                linkRepository.save(link);
                isFirst = false;
            }
        }

        log.info("Category created: id={} slug={} code={} depth={} parents={}",
                category.getId(), category.getSlug(), category.getCode(), category.getDepth(),
                parents.stream().map(Category::getName).collect(Collectors.joining(", ")));

        return CategoryResponse.from(category);
    }

    // ── Admin: hierarchy link management ─────────────────────────────────────

    /**
     * Adds a parent to an existing GROUP or LEAF category — enabling cross-listing.
     *
     * <p><b>Constraints:</b>
     * <ul>
     *   <li>child must not be ROOT</li>
     *   <li>parent must be exactly one depth level above child</li>
     *   <li>parent must be active</li>
     *   <li>parent ≠ child</li>
     * </ul>
     */
    @Transactional
    public CategoryHierarchyLinkResponse addParentToCategory(Long childId, AddHierarchyLinkRequest request) {
        Category child  = findNonRootById(childId);
        Category parent = findCategoryById(request.getParentId());

        if (child.getId().equals(parent.getId())) {
            throw new IllegalArgumentException("A category cannot be its own parent.");
        }
        if (parent.isLeaf()) {
            throw new IllegalArgumentException(
                    "LEAF categories cannot be parents. '" + parent.getName() + "' is a LEAF.");
        }
        if (parent.getDepth() != child.getDepth() - 1) {
            throw new IllegalArgumentException(
                    "Parent must be exactly one level above child. " +
                    "Parent depth=" + parent.getDepth() + ", child depth=" + child.getDepth());
        }
        if (!parent.isActive()) {
            throw new IllegalArgumentException(
                    "Cannot link to inactive parent category '" + parent.getName() + "'.");
        }

        // Idempotency: return existing link unchanged
        Optional<CategoryHierarchyLink> existing =
                linkRepository.findByParentIdAndChildId(parent.getId(), child.getId());
        if (existing.isPresent()) {
            log.info("Hierarchy link already exists: parent={} child={} — returning existing.",
                    parent.getName(), child.getName());
            return CategoryHierarchyLinkResponse.from(existing.get());
        }

        boolean isPrimary = !linkRepository.hasPrimaryLink(child.getId());

        CategoryHierarchyLink link = CategoryHierarchyLink.builder()
                .parent(parent)
                .child(child)
                .primary(isPrimary)
                .displayOrder(request.getDisplayOrder())
                .visible(request.isVisible())
                .navigationLabel(request.getNavigationLabel())
                .build();

        link = linkRepository.save(link);

        log.info("Hierarchy link created: parent={} child={} primary={}",
                parent.getName(), child.getName(), isPrimary);
        return CategoryHierarchyLinkResponse.from(link);
    }

    /**
     * Removes a parent from a GROUP or LEAF category (removes the cross-listing).
     * A category must always retain at least one parent link.
     */
    @Transactional
    public void removeParentFromCategory(Long childId, Long parentId) {
        Category child  = findNonRootById(childId);
        Category parent = findCategoryById(parentId);

        CategoryHierarchyLink link = linkRepository
                .findByParentIdAndChildId(parent.getId(), child.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hierarchy link found between parent '" + parent.getName()
                        + "' and child '" + child.getName() + "'."));

        long linkCount = linkRepository.countByChildId(child.getId());
        if (linkCount <= 1) {
            throw new IllegalArgumentException(
                    "Cannot remove the last parent of '" + child.getName() +
                    "'. A category must have at least one parent.");
        }

        boolean wasPrimary = link.isPrimary();
        linkRepository.deleteByParentIdAndChildId(parent.getId(), child.getId());

        // Promote next link to primary if we removed the primary
        if (wasPrimary) {
            List<CategoryHierarchyLink> remaining = linkRepository.findAllByChildId(child.getId());
            if (!remaining.isEmpty()) {
                CategoryHierarchyLink promoted = remaining.get(0);
                promoted.setPrimary(true);
                linkRepository.save(promoted);
                log.info("Primary link promoted after removal: new primary parent={}",
                        promoted.getParent().getName());
            }
        }

        log.info("Hierarchy link removed: parent={} child={}", parent.getName(), child.getName());
    }

    /**
     * Updates metadata on an existing hierarchy link (displayOrder, visibility, label).
     */
    @Transactional
    public CategoryHierarchyLinkResponse updateHierarchyLink(
            Long childId, Long parentId, UpdateHierarchyLinkRequest request) {

        Category child  = findNonRootById(childId);
        Category parent = findCategoryById(parentId);

        CategoryHierarchyLink link = linkRepository
                .findByParentIdAndChildId(parent.getId(), child.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hierarchy link found between parent '" + parent.getName()
                        + "' and child '" + child.getName() + "'."));

        if (request.getDisplayOrder() != null) {
            link.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getVisible() != null) {
            link.setVisible(request.getVisible());
        }
        if (request.getNavigationLabel() != null) {
            String label = request.getNavigationLabel().isBlank() ? null : request.getNavigationLabel().trim();
            link.setNavigationLabel(label);
        }

        link = linkRepository.save(link);
        log.info("Hierarchy link updated: parent={} child={}", parent.getName(), child.getName());
        return CategoryHierarchyLinkResponse.from(link);
    }

    /**
     * Promotes a specific parent link to the canonical primary parent.
     * Syncs {@code categories.parent_id} to match.
     */
    @Transactional
    public CategoryHierarchyLinkResponse setPrimaryParent(Long childId, Long parentId) {
        Category child  = findNonRootById(childId);
        Category parent = findCategoryById(parentId);

        CategoryHierarchyLink targetLink = linkRepository
                .findByParentIdAndChildId(parent.getId(), child.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No hierarchy link found between parent '" + parent.getName()
                        + "' and child '" + child.getName() + "'. Add the parent first."));

        linkRepository.clearPrimaryFlagForChild(child.getId());
        targetLink.setPrimary(true);
        linkRepository.save(targetLink);

        child.setParent(parent);
        categoryRepository.save(child);

        log.info("Primary parent set: child={} new primary parent={}", child.getName(), parent.getName());
        return CategoryHierarchyLinkResponse.from(targetLink);
    }

    /**
     * Reorders the children of a parent by applying sequential displayOrder values.
     */
    @Transactional
    public List<CategoryHierarchyLinkResponse> reorderCategoryChildren(
            Long parentId, List<Long> orderedChildIds) {

        findCategoryById(parentId);
        List<CategoryHierarchyLink> links = linkRepository.findAllByParentId(parentId);

        Map<Long, CategoryHierarchyLink> linkByChildId = links.stream()
                .collect(Collectors.toMap(l -> l.getChild().getId(), l -> l));

        List<CategoryHierarchyLink> updated = new ArrayList<>();
        for (int i = 0; i < orderedChildIds.size(); i++) {
            Long childId = orderedChildIds.get(i);
            CategoryHierarchyLink link = linkByChildId.get(childId);
            if (link == null) {
                throw new ResourceNotFoundException(
                        "Child category id=" + childId + " is not linked to parent id=" + parentId);
            }
            link.setDisplayOrder(i);
            updated.add(linkRepository.save(link));
        }

        log.info("Reordered {} children under parent id={}", updated.size(), parentId);
        return updated.stream()
                .map(CategoryHierarchyLinkResponse::from)
                .collect(Collectors.toList());
    }

    // ── Admin: update category details ───────────────────────────────────────

    /**
     * Updates the display metadata of an existing category.
     *
     * <p><b>Code is NOT updatable</b> — embedded in all variant SKUs.
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: id=" + id));

        final String oldSlug = category.getSlug();
        final String oldName = category.getName();

        category.setName(request.getName().trim());

        // Slug resolution
        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String explicitSlug = request.getSlug().trim().toLowerCase();
            if (categoryRepository.existsBySlugAndIdNot(explicitSlug, id)) {
                throw new ConflictException(
                        "Slug '" + explicitSlug + "' is already taken by another category.");
            }
            category.setSlug(explicitSlug);
        } else if (!request.getName().trim().equalsIgnoreCase(oldName)) {
            String newSlug = SlugUtils.toUniqueSlug(
                    request.getName(),
                    slug -> categoryRepository.existsBySlugAndIdNot(slug, id)
            );
            category.setSlug(newSlug);
            log.warn("Category slug changed: id={} old='{}' new='{}' — old URL will return 404.",
                    id, oldSlug, newSlug);
        }

        if (request.getDescription() != null) {
            category.setDescription(request.getDescription().isBlank() ? null : request.getDescription().trim());
        }
        if (request.getImageUrl() != null) {
            category.setImageUrl(request.getImageUrl().isBlank() ? null : request.getImageUrl().trim());
        }
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }

        category = categoryRepository.save(category);
        log.info("Category updated: id={} name='{}' slug='{}'", id, category.getName(), category.getSlug());
        return CategoryResponse.from(category);
    }

    /**
     * Soft-deactivates a category (sets active=false).
     */
    @Transactional
    public void deactivateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: id=" + id));
        category.setActive(false);
        categoryRepository.save(category);
        log.info("Category deactivated: id={} name={}", id, category.getName());
    }

    /**
     * Re-activates a previously deactivated category.
     */
    @Transactional
    public void activateCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: id=" + id));
        if (category.isActive()) {
            throw new ConflictException("Category is already active: id=" + id);
        }
        category.setActive(true);
        categoryRepository.save(category);
        log.info("Category re-activated: id={} name={}", id, category.getName());
    }

    /**
     * Permanently removes a category. Pre-conditions:
     * 1. Must be soft-deactivated first.
     * 2. No products may reference this category.
     * 3. No child categories may exist.
     */
    @Transactional
    public void hardDeleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: id=" + id));

        if (category.isActive()) {
            throw new IllegalStateException(
                "Category '" + category.getName() + "' is still active. Deactivate it first.");
        }
        if (productRepository.existsByCategory_Id(id)) {
            throw new IllegalStateException(
                "Category '" + category.getName() + "' has products. Reassign them first.");
        }
        if (categoryRepository.existsByParentId(id)) {
            throw new IllegalStateException(
                "Category '" + category.getName() + "' still has child categories. Remove them first.");
        }

        linkRepository.deleteAllByCategoryId(id);
        categoryRepository.deleteById(id);

        log.info("Category HARD-DELETED: id={} name={}", id, category.getName());
    }

    // ── Internal (used by ProductService) ────────────────────────────────────

    /** Used by ProductService to resolve category by ID. */
    @Transactional(readOnly = true)
    public Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: id=" + id));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Finds a category by ID. No depth validation — used for generic lookups.
     */
    private Category findCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: id=" + id));
    }

    /**
     * Finds and validates that the given ID is an existing non-ROOT category (GROUP or LEAF).
     *
     * @throws IllegalArgumentException if the category is a ROOT
     */
    private Category findNonRootById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: id=" + id));
        if (category.isRoot()) {
            throw new IllegalArgumentException(
                    "Category '" + category.getName() + "' is a ROOT category. " +
                    "ROOT categories cannot be assigned additional parents.");
        }
        return category;
    }
}
