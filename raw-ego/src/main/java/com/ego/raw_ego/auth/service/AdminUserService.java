package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.dto.response.AdminUserResponse;
import com.ego.raw_ego.auth.repository.UserRepository;
import com.ego.raw_ego.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin service for user management operations.
 *
 * <p>Exposes two read-only use cases:
 * <ul>
 *   <li>Paginated user list with optional search — used by {@code GET /api/v1/admin/users}</li>
 *   <li>Single user lookup — used by {@code GET /api/v1/admin/users/{id}}</li>
 * </ul>
 *
 * <p>Security: password hashes and security-sensitive fields are excluded at the DTO
 * layer ({@link AdminUserResponse#from(com.ego.raw_ego.auth.entity.User)}) — this service
 * never exposes the raw {@link com.ego.raw_ego.auth.entity.User} entity to callers.
 *
 * <p>Soft-delete transparency: all queries filter {@code deleted = false} — soft-deleted
 * users are invisible to admin management views (same as the rest of the application).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    /**
     * Returns a paginated list of non-deleted users.
     *
     * <p>When {@code search} is provided (non-blank), performs a case-insensitive
     * LIKE search across email, firstName, and lastName. Otherwise returns all users.
     *
     * <p>No N+1 risk: {@code Page<User>} → {@code Page<AdminUserResponse>} is mapped
     * via {@code .map(AdminUserResponse::from)} which accesses only already-loaded
     * scalar fields on the {@code User} entity — no lazy associations are accessed.
     *
     * @param search  optional search term (email / name fragment); null or blank = no filter
     * @param pageable pagination parameters (page, size, sort)
     * @return paginated page of {@link AdminUserResponse} — never null
     */
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            log.debug("Admin user search: term=\"{}\" page={} size={}",
                    search.trim(), pageable.getPageNumber(), pageable.getPageSize());
            return userRepository.searchByTerm(search.trim(), pageable)
                    .map(AdminUserResponse::from);
        }
        log.debug("Admin user list: page={} size={}",
                pageable.getPageNumber(), pageable.getPageSize());
        return userRepository.findByDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(AdminUserResponse::from);
    }

    /**
     * Returns a single non-deleted user by ID.
     *
     * @param id the user's primary key
     * @return {@link AdminUserResponse} without sensitive fields
     * @throws ResourceNotFoundException if the user does not exist or has been soft-deleted
     */
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: id=" + id));
    }
}
