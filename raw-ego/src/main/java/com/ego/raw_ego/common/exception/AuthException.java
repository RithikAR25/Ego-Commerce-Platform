package com.ego.raw_ego.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when authentication fails or a JWT is invalid/expired/revoked.
 * Maps to HTTP 401 Unauthorized by default.
 */
public class AuthException extends EgoException {

    public AuthException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }

    /** Use when you need a different status, e.g. 403 for locked accounts. */
    public AuthException(String message, HttpStatus status) {
        super(message, status);
    }
}
