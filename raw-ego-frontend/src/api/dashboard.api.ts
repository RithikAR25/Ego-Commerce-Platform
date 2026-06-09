/**
 * dashboard.api.ts
 *
 * Admin Dashboard KPI API — calls GET /api/v1/admin/dashboard/summary.
 * Backend: com.ego.raw_ego.admin.dashboard.controller.DashboardController
 */
import apiClient from './client';
import type { ApiResponse } from '@/types/api.types';
import type { DashboardSummary } from '@/types/dashboard.types';

/**
 * Admin: fetches the dashboard KPI summary.
 * Requires ADMIN JWT — handled by apiClient interceptor.
 */
export const getDashboardSummary = async (): Promise<DashboardSummary> => {
  const { data } = await apiClient.get<ApiResponse<DashboardSummary>>(
    '/admin/dashboard/summary',
  );
  return data.data;
};
