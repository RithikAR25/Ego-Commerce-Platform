package com.ego.raw_ego.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a domain business rule is violated and the operation cannot proceed.
 *
 * <p>Maps to HTTP {@code 409 Conflict}. Use this for precondition failures that are
 * caused by the current <em>state</em> of the data — not by a bad request argument.
 *
 * <p>Examples:
 * <ul>
 *   <li>Attempting to hard-delete an active category.</li>
 *   <li>Attempting to hard-delete a category that still has products assigned.</li>
 *   <li>Attempting to hard-delete a category that still has child categories.</li>
 *   <li>Product code sequence reaching the 9999 limit.</li>
 * </ul>
 *
 * <p>Contrast with {@link IllegalArgumentException} (bad input value → 400) and
 * {@link ConflictException} (unique constraint collision → 409). This exception is for
 * <em>state-based</em> conflicts, while {@link ConflictException} is for <em>uniqueness</em> conflicts.
 *
 * @see GlobalExceptionHandler
 */
public class BusinessRuleViolationException extends EgoException {

    public BusinessRuleViolationException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
