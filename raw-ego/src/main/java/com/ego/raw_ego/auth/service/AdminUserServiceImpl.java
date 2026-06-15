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
 * Implementation of {@link AdminUserService} — admin service for user management operations.
 *
 * <p>Security: password hashes and security-sensitive fields are excluded at the DTO
 * layer ({@link AdminUserResponse#from}) — this service never exposes the raw User entity to callers.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long id) {
        return userRepository.findByIdAndDeletedFalse(id)
                .map(AdminUserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: id=" + id));
    }
}
