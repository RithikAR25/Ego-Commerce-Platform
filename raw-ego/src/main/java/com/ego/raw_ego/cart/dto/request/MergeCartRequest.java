package com.ego.raw_ego.cart.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for merging an anonymous cart into an authenticated user's cart.
 *
 * <p>Called immediately after login with the browser-generated session ID
 * (stored in localStorage as {@code ego_session_id}).
 * If the session has no cart, the merge is a no-op.
 */
@Getter
@Setter
@NoArgsConstructor
public class MergeCartRequest {

    @NotBlank(message = "sessionId is required")
    private String sessionId;
}
