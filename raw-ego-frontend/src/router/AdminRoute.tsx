/**
 * AdminRoute.tsx
 *
 * Guards routes that require ADMIN role specifically.
 *
 * Behavior:
 *   - If NOT authenticated: redirect to /login
 *   - If authenticated but CUSTOMER role: redirect to /403 (not 404 — we don't
 *     hide that admin routes exist, but we don't grant access)
 *   - If ADMIN: renders children
 *
 * IMPORTANT:
 *   - The frontend role check is for UX only.
 *   - The backend enforces .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
 *     at the SecurityConfig level. Even if this guard is bypassed, every admin
 *     API call will return 403.
 *   - Never redirect to /404 for role failures — use /403. This tells the user
 *     they don't have access (vs the page not existing).
 */

import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import type { ReactNode } from 'react';

interface AdminRouteProps {
  children: ReactNode;
}

const AdminRoute = ({ children }: AdminRouteProps) => {
  const { isAuthenticated, user } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== 'ADMIN') {
    return <Navigate to="/403" replace />;
  }

  return <>{children}</>;
};

export default AdminRoute;
