/* eslint-disable react-refresh/only-export-components */
/**
 * index.tsx (Router)
 *
 * Application route definitions using React Router v7 createBrowserRouter.
 *
 * Architecture decisions:
 *
 * 1. Lazy loading on every page component.
 *    Initial bundle is ~shell only. Each page loads on demand.
 *    Result: faster first paint, smaller initial JS.
 *
 * 2. Nested layout routes.
 *    RootLayout:     Navbar + Footer + AnnouncementBar (all public/customer pages)
 *    CheckoutLayout: Logo only (no nav — reduces cart abandonment)
 *    AdminLayout:    Admin sidebar + top bar (separate from storefront)
 *    All layouts are Outlet-based (React Router nested routing).
 *
 * 3. ProtectedRoute and AdminRoute are NOT layouts — they're wrappers.
 *    This keeps route guards composable and explicit.
 *
 * 4. 404 catch-all is the last route.
 *    Unknown paths show NotFoundPage — never expose backend route information.
 */

import { createBrowserRouter, Outlet } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import ProtectedRoute from '@/router/ProtectedRoute';
import AdminRoute     from '@/router/AdminRoute';
import PageLoader     from '@/components/ui/PageLoader';
import PageTransitionLoader from '@/components/ui/PageTransitionLoader';
import { HelmetProvider } from 'react-helmet-async';

// ── Lazy imports ─────────────────────────────────────────────────────────────
// Layout shells (small — load eagerly or near-eagerly)
const RootLayout      = lazy(() => import('@/components/layout/RootLayout'));
const CheckoutLayout  = lazy(() => import('@/components/layout/CheckoutLayout'));
const AdminLayout     = lazy(() => import('@/components/layout/AdminLayout'));

// ── Auth pages
const LoginPage    = lazy(() => import('@/features/auth/pages/LoginPage'));
const RegisterPage = lazy(() => import('@/features/auth/pages/RegisterPage'));
const VerifyEmailPage      = lazy(() => import('@/features/auth/pages/VerifyEmailPage'));
const ForgotPasswordPage   = lazy(() => import('@/features/auth/pages/ForgotPasswordPage'));
const ResetPasswordPage    = lazy(() => import('@/features/auth/pages/ResetPasswordPage'));

// ── Catalog Storefront Pages
const ProductListingPage = lazy(() => import('@/features/catalog/storefront/pages/ProductListingPage'));
const ProductDetailPage = lazy(() => import('@/features/catalog/storefront/pages/ProductDetailPage'));

// ── Catalog Admin Pages
const AdminProductsPage = lazy(() => import('@/features/catalog/admin/pages/AdminProductsPage'));
const AdminProductDetailPage = lazy(() => import('@/features/catalog/admin/pages/AdminProductDetailPage'));
const AdminCouponsPage = lazy(() => import('@/features/coupons/admin/pages/AdminCouponsPage'));
const AdminCategoriesPage = lazy(() => import('@/features/catalog/admin/pages/AdminCategoriesPage'));

// ── Phase 11 Admin Pages
const AdminDashboardPage = lazy(() => import('@/features/dashboard/admin/pages/AdminDashboardPage'));
const AdminOrdersPage = lazy(() => import('@/features/orders/admin/pages/AdminOrdersPage'));
const AdminOrderDetailPage = lazy(() => import('@/features/orders/admin/pages/AdminOrderDetailPage'));
const AdminReturnsPage = lazy(() => import('@/features/returns/admin/pages/AdminReturnsPage'));
const AdminReturnDetailPage = lazy(() => import('@/features/returns/admin/pages/AdminReturnDetailPage'));
const AdminInventoryPage = lazy(() => import('@/features/catalog/admin/pages/AdminInventoryPage'));

// ── Phase 12 Admin Pages (Users Management)
const AdminUsersPage      = lazy(() => import('@/features/users/admin/pages/AdminUsersPage'));
const AdminUserDetailPage = lazy(() => import('@/features/users/admin/pages/AdminUserDetailPage'));

// ── Placeholder pages (Phase 2+)
const HomePage      = lazy(() => import('@/pages/HomePage'));
const AccountPage   = lazy(() => import('@/pages/AccountPage'));
const NotFoundPage  = lazy(() => import('@/pages/NotFoundPage'));
const ForbiddenPage = lazy(() => import('@/pages/ForbiddenPage'));

// ── Phase 12 Consumer Storefront pages
const CheckoutPage             = lazy(() => import('@/features/checkout/pages/CheckoutPage'));
const PaymentVerificationPage  = lazy(() => import('@/features/checkout/pages/PaymentVerificationPage'));
const OrderSuccessPage         = lazy(() => import('@/features/checkout/pages/OrderSuccessPage'));
const CustomerOrdersPage       = lazy(() => import('@/features/orders/storefront/pages/CustomerOrdersPage'));
const CustomerOrderDetailPage  = lazy(() => import('@/features/orders/storefront/pages/CustomerOrderDetailPage'));
const WishlistPage             = lazy(() => import('@/features/wishlist/pages/WishlistPage'));
const CartPage                 = lazy(() => import('@/pages/CartPage'));

// ── Helper: wrap each lazy page in Suspense with page-level skeleton
const SuspensePage = ({ children }: { children: React.ReactNode }) => (
  <Suspense fallback={<PageLoader />}>{children}</Suspense>
);

const GlobalLayout = () => (
  <HelmetProvider>
    <PageTransitionLoader />
    <Outlet />
  </HelmetProvider>
);

// ── Router definition ─────────────────────────────────────────────────────────

const router = createBrowserRouter([
  {
    element: <GlobalLayout />,
    children: [
      // ── Public + Customer routes (with Navbar + Footer)
      {
        element: <SuspensePage><RootLayout /></SuspensePage>,
        children: [
          { index: true, element: <SuspensePage><HomePage /></SuspensePage> },
          
          // Auth routes (public — redirect to / if already authenticated)
          { path: 'login',    element: <SuspensePage><LoginPage /></SuspensePage> },
          { path: 'register', element: <SuspensePage><RegisterPage /></SuspensePage> },
          { path: 'auth/verify-email',    element: <SuspensePage><VerifyEmailPage /></SuspensePage> },
          { path: 'auth/forgot-password', element: <SuspensePage><ForgotPasswordPage /></SuspensePage> },
          { path: 'auth/reset-password',  element: <SuspensePage><ResetPasswordPage /></SuspensePage> },
          
          // Error pages
          { path: '403', element: <SuspensePage><ForbiddenPage /></SuspensePage> },
          { path: '404', element: <SuspensePage><NotFoundPage /></SuspensePage> },
          
          // ── Phase 2+ (catalog)
          { path: 'products',       element: <SuspensePage><ProductListingPage /></SuspensePage> },
          { path: 'products/:slug', element: <SuspensePage><ProductDetailPage /></SuspensePage> },
          
          // Protected customer routes
          {
            path: 'cart',
            element: <ProtectedRoute><SuspensePage><CartPage /></SuspensePage></ProtectedRoute>,
          },
          // Protected customer account routes
          {
            path: 'account',
            element: <ProtectedRoute><Outlet /></ProtectedRoute>,
            children: [
              { index: true, element: <SuspensePage><AccountPage /></SuspensePage> },
            ],
          },
          {
            path: 'wishlist',
            element: <ProtectedRoute><SuspensePage><WishlistPage /></SuspensePage></ProtectedRoute>,
          },
          {
            path: 'orders',
            element: (
              <ProtectedRoute>
                <Outlet />
              </ProtectedRoute>
            ),
            children: [
              { index: true, element: <SuspensePage><CustomerOrdersPage /></SuspensePage> },
              { path: ':orderId', element: <SuspensePage><CustomerOrderDetailPage /></SuspensePage> },
            ],
          },
        ],
      },

      // ── Checkout layout (no navbar — distraction-free)
      {
        element: (
          <ProtectedRoute>
            <SuspensePage><CheckoutLayout /></SuspensePage>
          </ProtectedRoute>
        ),
        children: [
          { path: 'checkout',           element: <SuspensePage><CheckoutPage /></SuspensePage> },
          { path: 'checkout/verify/:orderId',  element: <SuspensePage><PaymentVerificationPage /></SuspensePage> },
          { path: 'checkout/success/:orderId', element: <SuspensePage><OrderSuccessPage /></SuspensePage> },
        ],
      },

      // ── Admin layout (ADMIN role only)
      {
        path: 'admin',
        element: (
          <AdminRoute>
            <SuspensePage><AdminLayout /></SuspensePage>
          </AdminRoute>
        ),
        children: [
          {
            index: true,
            element: <SuspensePage><AdminDashboardPage /></SuspensePage>,
          },
          {
            path: 'categories',
            element: <SuspensePage><AdminCategoriesPage /></SuspensePage>,
          },
          {
            path: 'products',
            element: <SuspensePage><AdminProductsPage /></SuspensePage>,
          },
          {
            path: 'products/:id',
            element: <SuspensePage><AdminProductDetailPage /></SuspensePage>,
          },
          {
            path: 'orders',
            element: <SuspensePage><AdminOrdersPage /></SuspensePage>,
          },
          {
            path: 'orders/:id',
            element: <SuspensePage><AdminOrderDetailPage /></SuspensePage>,
          },
          {
            path: 'returns',
            element: <SuspensePage><AdminReturnsPage /></SuspensePage>,
          },
          {
            path: 'returns/:id',
            element: <SuspensePage><AdminReturnDetailPage /></SuspensePage>,
          },
          {
            path: 'inventory',
            element: <SuspensePage><AdminInventoryPage /></SuspensePage>,
          },
          {
            path: 'coupons',
            element: <SuspensePage><AdminCouponsPage /></SuspensePage>,
          },
          {
            path: 'users',
            element: <SuspensePage><AdminUsersPage /></SuspensePage>,
          },
          {
            path: 'users/:id',
            element: <SuspensePage><AdminUserDetailPage /></SuspensePage>,
          },
        ],
      },

      // ── 404 catch-all
      { path: '*', element: <SuspensePage><NotFoundPage /></SuspensePage> },
    ]
  }
]);

export default router;
