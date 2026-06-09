/**
 * ProtectedRoute.tsx
 *
 * Guards routes that require authentication (any logged-in user).
 *
 * Behavior:
 *   - If isAuthenticated: renders children.
 *   - If NOT authenticated: redirects to /login?redirect={currentPath}
 *     The redirect param allows the user to return to their intended page after login.
 *
 * When to use:
 *   /cart, /checkout, /orders/**, /account/**
 *
 * IMPORTANT: This is a UX guard only. The backend enforces all auth checks.
 * Even if someone bypasses this client-side guard, every API call will return 401.
 */

import { Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import type { ReactNode } from 'react';

interface ProtectedRouteProps {
  children: ReactNode;
}

const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();

  if (!isAuthenticated) {
    // Preserve the current path so we can redirect back after login
    const redirectParam = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirectParam}`} replace />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
