package com.ego.raw_ego.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a Cloudinary upload operation fails.
 *
 * <p>Maps to HTTP 500 (internal server error) because the failure is on our
 * infrastructure side, not a client input error.
 *
 * <p>Example: Cloudinary returns an error response, network timeout during upload.
 * The underlying cause is attached so it propagates to structured logging in
 * {@link GlobalExceptionHandler}.
 */
public class ImageUploadException extends EgoException {

    public ImageUploadException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    public ImageUploadException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
