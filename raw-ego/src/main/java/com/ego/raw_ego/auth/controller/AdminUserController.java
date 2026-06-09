package com.ego.raw_ego.auth.controller;

import com.ego.raw_ego.auth.dto.response.AdminUserResponse;
import com.ego.raw_ego.auth.service.AdminUserService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only REST controller for user management.
 *
 * <p>API surface:
 * <pre>
 * GET /api/v1/admin/users          → paginated user list (search, page, size)
 * GET /api/v1/admin/users/{id}     → single user profile
 * </pre>
 *
 * <p>Security: doubly guarded —
 * <ul>
 *   <li>Route-level: {@code /api/v1/admin/**} enforces {@code ROLE_ADMIN} in SecurityConfig.</li>
 *   <li>Method-level: {@code @PreAuthorize("hasRole('ADMIN')")} as an explicit guard.</li>
 * </ul>
 *
 * <p>Privacy: password hashes and all security-sensitive fields are stripped at the
 * {@link AdminUserResponse} DTO layer — entities are never serialized directly.
 */
@RestController
@Tag(name = "Admin – Users", description = "Admin user management — listing, search, and profile lookup")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    // ── GET /api/v1/admin/users ───────────────────────────────────────────────

    @GetMapping("/api/v1/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary     = "Admin: list users",
            description = "Returns a paginated list of all non-deleted users. " +
                          "Optionally filter by ?search=<email|firstName|lastName>. " +
                          "Newest users first by default. " +
                          "Password and all security-sensitive fields are never returned. " +
                          "Example: ?search=john&page=0&size=20"
    )
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> getUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        Page<AdminUserResponse> users = adminUserService.getUsers(search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved.", users));
    }

    // ── GET /api/v1/admin/users/{id} ─────────────────────────────────────────

    @GetMapping("/api/v1/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary     = "Admin: get user by ID",
            description = "Returns the admin-safe profile for a specific user. " +
                          "Returns 404 if the user does not exist or has been soft-deleted. " +
                          "Password, version, and security-sensitive fields are never returned."
    )
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable Long id) {
        AdminUserResponse user = adminUserService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success("User retrieved.", user));
    }
}
