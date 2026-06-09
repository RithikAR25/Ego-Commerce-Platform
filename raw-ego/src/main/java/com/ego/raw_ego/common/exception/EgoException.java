package com.ego.raw_ego.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain-level exceptions in the EGO platform.
 *
 * Carries an HTTP status so that {@link GlobalExceptionHandler} can
 * return the correct response code without needing a long if-else chain.
 *
 * <p>Never throw this base class directly — use a specific subclass
 * (AuthException, ResourceNotFoundException, ConflictException, etc.)
 */
public class EgoException extends RuntimeException {

    private final HttpStatus status;

    public EgoException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public EgoException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
