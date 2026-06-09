/**
 * api.types.ts
 *
 * Core API response envelope types — mirrors the backend's ApiResponse<T>
 * and ApiError structures defined in com.ego.raw_ego.common.response.
 *
 * RULE: Every API call in this project must type its response using these envelopes.
 * Never use raw `axios.get<SomeType>` without the ApiResponse wrapper.
 */

// ── Core envelope ─────────────────────────────────────────────────────────────

/**
 * Standard success response from the EGO backend.
 * All endpoints return: { success: true, message: "...", data: T, timestamp: "..." }
 */
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

/**
 * Standard error response — returned by GlobalExceptionHandler on 4xx/5xx.
 * The `errors` map contains field-level validation errors from @Valid failures.
 */
export interface ApiErrorResponse {
  success: false;
  message: string;
  errors?: Array<{ field: string; message: string }>;
  timestamp: string;
}

/**
 * Paginated response wrapper — used for product search, order lists, etc.
 * Matches Spring's Page<T> serialization.
 */
export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  first: boolean;
}
