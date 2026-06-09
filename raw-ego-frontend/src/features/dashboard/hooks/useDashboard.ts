/**
 * useDashboard.ts
 */
import { useQuery } from '@tanstack/react-query';
import { getDashboardSummary } from '@/api/dashboard.api';

export const dashboardKeys = {
  all: ['dashboard'] as const,
  summary: () => [...dashboardKeys.all, 'summary'] as const,
};

/**
 * Returns dashboard summary KPIs.
 * 
 * TODO: This expects GET /api/v1/admin/dashboard/summary to exist.
 */
export const useDashboardSummary = () => {
  return useQuery({
    queryKey: dashboardKeys.summary(),
    queryFn: () => getDashboardSummary(),
    staleTime: 60_000,
    retry: false, // Don't retry since endpoint might not exist yet
  });
};
