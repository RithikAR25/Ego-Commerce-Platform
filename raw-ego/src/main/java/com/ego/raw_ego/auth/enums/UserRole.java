package com.ego.raw_ego.auth.enums;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Set;

/**
 * User roles used throughout the application.
 *
 * <p>DB stores lowercase ("customer", "admin") to match the MySQL ENUM definition.
 * Spring Security uses "ROLE_CUSTOMER" / "ROLE_ADMIN" convention.
 * This enum bridges both worlds via {@link #getDbValue()} and {@link #toGrantedAuthority()}.
 *
 * <p>Usage in security expressions:
 * <pre>
 *   @PreAuthorize("hasRole('ADMIN')")      // note: no "ROLE_" prefix in expressions
 *   @PreAuthorize("hasRole('CUSTOMER')")
 * </pre>
 */
public enum UserRole {

    CUSTOMER("customer"),
    ADMIN("admin");

    private final String dbValue;

    UserRole(String dbValue) {
        this.dbValue = dbValue;
    }

    /** Returns the lowercase value stored in the MySQL ENUM column. */
    public String getDbValue() {
        return dbValue;
    }

    /** Returns the Spring Security authority for this role. */
    public GrantedAuthority toGrantedAuthority() {
        return new SimpleGrantedAuthority("ROLE_" + this.name());
    }

    /** Returns the single-element authority set for {@code UserDetails.getAuthorities()}. */
    public Set<GrantedAuthority> toGrantedAuthorities() {
        return Set.of(toGrantedAuthority());
    }
}
