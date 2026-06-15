package com.ego.raw_ego.auth.service;

import com.ego.raw_ego.auth.dto.response.AdminUserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contract for admin user management operations.
 *
 * <p>Exposes two read-only use cases:
 * <ul>
 *   <li>Paginated user list with optional search</li>
 *   <li>Single user lookup</li>
 * </ul>
 *
 * <p>Soft-delete transparency: all queries filter {@code deleted = false}.
 */
public interface AdminUserService {

    /**
     * Returns a paginated list of non-deleted users.
     *
     * @param search  optional search term (email / name fragment); null or blank = no filter
     * @param pageable pagination parameters
     */
    Page<AdminUserResponse> getUsers(String search, Pageable pageable);

    /**
     * Returns a single non-deleted user by ID.
     *
     * @throws com.ego.raw_ego.common.exception.ResourceNotFoundException if not found or soft-deleted
     */
    AdminUserResponse getUser(Long id);
}
