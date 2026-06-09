package com.ego.raw_ego.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Standardized API response envelope used across the entire application.
 *
 * <pre>
 * {
 *   "success": true,
 *   "message": "Login successful.",
 *   "data": { ... },
 *   "errors": null,          // omitted when null
 *   "timestamp": "2026-05-21T09:00:00Z"
 * }
 * </pre>
 *
 * Use the static factory methods — never instantiate directly.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final List<ApiError> errors;
    private final Instant timestamp;

    private ApiResponse(boolean success, String message, T data, List<ApiError> errors) {
        this.success   = success;
        this.message   = message;
        this.data      = data;
        this.errors    = errors;
        this.timestamp = Instant.now();
    }

    // ── Success factories ─────────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data, null);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    // ── Error factories ───────────────────────────────────────────────────────

    public static <T> ApiResponse<T> error(String message, List<ApiError> errors) {
        return new ApiResponse<>(false, message, null, errors);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null);
    }
}
