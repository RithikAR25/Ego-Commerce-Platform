package com.ego.raw_ego.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents a single field-level validation error within {@link ApiResponse}.
 *
 * <pre>
 * { "field": "email", "message": "Please provide a valid email address" }
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public class ApiError {

    /** The DTO field name that failed validation. Null for global errors. */
    private final String field;

    /** Human-readable error message. */
    private final String message;

    public static ApiError of(String field, String message) {
        return new ApiError(field, message);
    }

    public static ApiError global(String message) {
        return new ApiError(null, message);
    }
}
