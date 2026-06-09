package com.ego.raw_ego.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a unique constraint would be violated, e.g. duplicate email on registration.
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends EgoException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
