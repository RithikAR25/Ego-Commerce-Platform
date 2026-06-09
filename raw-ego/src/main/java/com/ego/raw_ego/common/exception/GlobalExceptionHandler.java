package com.ego.raw_ego.common.exception;

import com.ego.raw_ego.common.response.ApiError;
import com.ego.raw_ego.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centralized exception handler for the entire application.
 *
 * <p>Precedence order (most-specific first):
 * <ol>
 *   <li>{@link MethodArgumentNotValidException}       — @Valid failures → 400</li>
 *   <li>{@link IllegalArgumentException}               — bad business rule input → 400</li>
 *   <li>{@link EgoException} (and subclasses)          — domain errors → their status</li>
 *   <li>{@link DataIntegrityViolationException}        — DB unique/FK constraint → 409</li>
 *   <li>{@link AuthenticationException}                — Spring Security auth failures → 401</li>
 *   <li>{@link AccessDeniedException}                  — Spring Security authz failures → 403</li>
 *   <li>{@link Exception}                              — unexpected errors → 500</li>
 * </ol>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Validation (400) ──────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> ApiError.of(fe.getField(), fe.getDefaultMessage()))
                .toList();

        log.warn("Validation failed — {} field error(s)", errors.size());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error("Validation failed. Please check the request and try again.", errors));
    }

    // ── Bad business-rule argument (400) ─────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request — illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── Invalid query / sort parameter (400) ─────────────────────────────────

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidDataAccess(InvalidDataAccessApiUsageException ex) {
        log.warn("Invalid data access usage: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(
                        "Invalid query parameter. Use valid field names for sort (e.g. createdAt, name, id)."));
    }

    // ── Domain exceptions (their own status) ──────────────────────────────────

    @ExceptionHandler(EgoException.class)
    public ResponseEntity<ApiResponse<Void>> handleEgoException(EgoException ex) {
        log.warn("Domain exception [{}]: {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── DB constraint violations (409) ────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("DB constraint violation: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("A database constraint was violated: " +
                        ex.getMostSpecificCause().getMessage()));
    }

    // ── Spring Security Auth (401) ────────────────────────────────────────────
    // Note: Spring Security normally handles these before they reach controllers.
    // This handler is a safety net for service-layer throws.

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSpringAuthentication(AuthenticationException ex) {
        log.warn("Spring Security authentication exception: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed."));
    }

    // ── Spring Security Authz (403) ───────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied. You do not have permission to perform this action."));
    }

    // ── Catch-all (500) ───────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        // Full stack trace printed to console — check IntelliJ run log for details
        log.error("Unhandled exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred [" + ex.getClass().getSimpleName() + "]: " +
                        ex.getMessage()));
    }
}
