package com.ego.raw_ego.auth.security;

import com.ego.raw_ego.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security's UserDetailsService implementation.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@link SecurityConfig} — wired into DaoAuthenticationProvider</li>
 *   <li>{@link JwtAuthenticationFilter} — loads UserDetails from the JWT subject</li>
 * </ul>
 *
 * <p>Returns the {@link com.ego.raw_ego.auth.entity.User} entity directly
 * (which implements UserDetails) — no adapter wrapper needed.
 *
 * <p>{@code @Transactional(readOnly = true)} routes this query to a read replica
 * when a read-replica routing data source is configured (Phase 11+).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> {
                    log.debug("UserDetailsService: user not found for email={}", email);
                    // Generic message — don't reveal whether the email exists
                    return new UsernameNotFoundException("User not found");
                });
    }
}
