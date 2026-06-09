package com.ego.raw_ego.common.constants;

/**
 * Application-wide constants.
 * All path constants are resolved from here — change once, propagates everywhere.
 */
public final class AppConstants {

    private AppConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ── API Paths ─────────────────────────────────────────────────────────────
    public static final String API_V1          = "/api/v1";
    public static final String AUTH_BASE_PATH  = API_V1 + "/auth";
    public static final String ADMIN_BASE_PATH = API_V1 + "/admin";

    // ── HTTP Headers ──────────────────────────────────────────────────────────
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX         = "Bearer ";

    // ── Security ──────────────────────────────────────────────────────────────
    /** BCrypt work factor — strength 12 is ~250ms on modern hardware. */
    public static final int BCRYPT_STRENGTH = 12;
}
