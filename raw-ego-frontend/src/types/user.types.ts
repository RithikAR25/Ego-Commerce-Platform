/**
 * user.types.ts
 *
 * TypeScript mirror of AdminUserResponse.java.
 * Field names match backend DTO exactly (camelCase — Jackson default serialization).
 * Password and all security-sensitive fields are excluded by design.
 */

/**
 * Admin-safe user profile returned by:
 *   GET /api/v1/admin/users
 *   GET /api/v1/admin/users/{id}
 *
 * Mirrors: com.ego.raw_ego.auth.dto.response.AdminUserResponse
 */
export interface AdminUserResponse {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  /** 'CUSTOMER' | 'ADMIN' — matches UserRole enum name */
  role: 'CUSTOMER' | 'ADMIN';
  /** true = active account, false = suspended */
  active: boolean;
  /** ISO-8601 registration timestamp */
  createdAt: string;
}
