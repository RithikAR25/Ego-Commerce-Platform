/**
 * admin.users.api.ts
 *
 * Admin Users Management API functions.
 * Backend: com.ego.raw_ego.auth.controller.AdminUserController
 *
 * Endpoints:
 *   GET /api/v1/admin/users          → paginated + searchable user list
 *   GET /api/v1/admin/users/{id}     → single user profile
 */

import apiClient from './client';
import type { ApiResponse, PaginatedResponse } from '@/types/api.types';
import type { AdminUserResponse } from '@/types/user.types';

/**
 * Admin: fetches a paginated list of users.
 *
 * @param page    0-indexed page number (default 0)
 * @param size    page size (default 20)
 * @param search  optional search term — matched against email, firstName, lastName
 */
export const getAdminUsers = async (
  page = 0,
  size = 20,
  search?: string,
): Promise<PaginatedResponse<AdminUserResponse>> => {
  const params: Record<string, string | number> = { page, size };
  if (search?.trim()) {
    params.search = search.trim();
  }

  const { data } = await apiClient.get<ApiResponse<PaginatedResponse<AdminUserResponse>>>(
    '/admin/users',
    { params },
  );
  return data.data;
};

/**
 * Admin: fetches a single user profile by ID.
 *
 * @param id  the user's primary key
 * @throws  AxiosError with status 404 if user does not exist or is soft-deleted
 */
export const getAdminUser = async (id: number): Promise<AdminUserResponse> => {
  const { data } = await apiClient.get<ApiResponse<AdminUserResponse>>(
    `/admin/users/${id}`,
  );
  return data.data;
};
