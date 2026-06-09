/**
 * useAdminUsers.ts
 *
 * React Query hooks for admin user management.
 * Mirrors the pattern used by useAdminReturns.ts.
 */
import { useQuery } from '@tanstack/react-query';
import { getAdminUsers, getAdminUser } from '@/api/admin.users.api';

// ── Query key factory ─────────────────────────────────────────────────────────

export const adminUserKeys = {
  all:    ['admin', 'users'] as const,
  list:   (page: number, size: number, search?: string) =>
            [...adminUserKeys.all, 'list', { page, size, search }] as const,
  detail: (id: number) =>
            [...adminUserKeys.all, 'detail', id] as const,
};

// ── Hooks ─────────────────────────────────────────────────────────────────────

/**
 * Paginated user list hook.
 * Refetches automatically when page, size, or search changes.
 *
 * @param page    0-indexed page number
 * @param size    records per page
 * @param search  optional search term (email / name)
 */
export const useAdminUsers = (page: number, size: number, search?: string) => {
  return useQuery({
    queryKey: adminUserKeys.list(page, size, search),
    queryFn:  () => getAdminUsers(page, size, search),
    staleTime: 30_000,
    placeholderData: (prev) => prev, // keep previous page visible while next loads
  });
};

/**
 * Single user detail hook.
 *
 * @param id  user primary key — query is disabled when falsy
 */
export const useAdminUser = (id: number) => {
  return useQuery({
    queryKey: adminUserKeys.detail(id),
    queryFn:  () => getAdminUser(id),
    staleTime: 60_000,
    enabled:   !!id,
  });
};
