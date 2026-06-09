package com.ego.raw_ego.catalog.controller;

import com.ego.raw_ego.catalog.dto.request.CreateProductRequest;
import com.ego.raw_ego.catalog.dto.request.UpdateProductStatusRequest;
import com.ego.raw_ego.catalog.dto.response.ProductDetailResponse;
import com.ego.raw_ego.catalog.dto.response.ProductSummaryResponse;
import com.ego.raw_ego.catalog.service.ProductService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Product REST controller.
 *
 * <p>Public endpoints (storefront — ACTIVE + OUT_OF_STOCK only):
 * <pre>
 *   GET /api/v1/products                       → paginated product listing
 *   GET /api/v1/products/{slug}                → product detail page
 *   GET /api/v1/products/category/{catSlug}    → products by subcategory
 * </pre>
 *
 * <p>Admin endpoints (all statuses):
 * <pre>
 *   GET    /api/v1/admin/products              → all products
 *   GET    /api/v1/admin/products/{slug}       → product detail (any status)
 *   POST   /api/v1/admin/products              → create product (DRAFT)
 *   PATCH  /api/v1/admin/products/{id}/status  → change status
 *   DELETE /api/v1/admin/products/{id}         → archive product
 * </pre>
 */
@RestController
@Tag(name = "Products", description = "Product catalog — storefront and admin")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ── Public ───────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/products")
    @Operation(summary = "List all products",
               description = "Returns paginated ACTIVE and OUT_OF_STOCK products. Default page size: 24.")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getProducts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "24") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(productService.getPublicProducts(pageable)));
    }

    @GetMapping("/api/v1/products/{slug}")
    @Operation(summary = "Get product detail",
               description = "Returns full product detail including all active variants, " +
                             "attribute matrix, and images. Used for the Product Detail Page.")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlug(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProductBySlug(slug)));
    }

    @GetMapping("/api/v1/products/category/{categorySlug}")
    @Operation(summary = "Get products by category",
               description = "Returns paginated products belonging to the specified subcategory slug.")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getProductsByCategory(
            @PathVariable String categorySlug,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "24") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(
                productService.getProductsByCategory(categorySlug, pageable)));
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @GetMapping("/api/v1/admin/products")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[Admin] List all products",
               description = "Returns all products across all statuses (DRAFT, ACTIVE, OUT_OF_STOCK, ARCHIVED).")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> getAllProductsAdmin(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(productService.getAllProductsAdmin(pageable)));
    }

    @GetMapping("/api/v1/admin/products/{slug}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[Admin] Get product detail (any status)",
               description = "Returns full product detail regardless of status. For admin editing.")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlugAdmin(
            @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProductBySlugAdmin(slug)));
    }

    @PostMapping("/api/v1/admin/products")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[Admin] Create product",
               description = "Creates a new product in DRAFT status. " +
                             "Category must be a subcategory (depth=1). " +
                             "slug and productCode are auto-generated.")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductDetailResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully.", response));
    }

    @PatchMapping("/api/v1/admin/products/{id}/status")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[Admin] Update product status",
               description = "Transitions the product status. " +
                             "Valid transitions: DRAFT→ACTIVE, DRAFT→ARCHIVED, " +
                             "ACTIVE→ARCHIVED, ARCHIVED→DRAFT. " +
                             "ACTIVE↔OUT_OF_STOCK is managed automatically by inventory changes.")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Product status updated.", productService.updateStatus(id, request)));
    }

    @DeleteMapping("/api/v1/admin/products/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "[Admin] Archive product",
               description = "Soft-archives the product — hidden from storefront. Reversible via status update.")
    public ResponseEntity<ApiResponse<Void>> archiveProduct(@PathVariable Long id) {
        productService.archiveProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product archived."));
    }

    @DeleteMapping("/api/v1/admin/products/{id}/permanent")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
        summary = "[Admin] Permanently delete a product",
        description = """
            Irreversibly removes the product and all related data (variants, images, \
            attribute types, reviews, and search-outbox entries).
            
            Two guards are enforced:
            1. Product must already be ARCHIVED (soft-deleted).
            2. No order_items rows may reference any of its variants — order history \
               is a legal record and cannot be orphaned.
            
            Returns 204 No Content on success.
            Returns 409 Conflict if either guard fails.
            """)
    public ResponseEntity<Void> hardDeleteProduct(@PathVariable Long id) {
        productService.hardDeleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
