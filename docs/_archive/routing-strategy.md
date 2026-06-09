# Routing Strategy — EGO Platform Frontend

> **Library:** React Router v7 (Data Router / `createBrowserRouter`)  
> **Pattern:** Lazy-loaded routes + nested layouts + role guards

---

## Route Map

```
/                          → HomePage                [Public]
/login                     → LoginPage               [Public, redirect if authed]
/register                  → RegisterPage            [Public, redirect if authed]

/products                  → ProductListingPage      [Public]
/products/:slug            → ProductDetailPage       [Public]
/collections/:categorySlug → ProductListingPage      [Public, prefilled filter]

/cart                      → CartPage (fallback)     [ProtectedRoute → CUSTOMER]
/checkout                  → CheckoutPage            [ProtectedRoute → CUSTOMER]
/checkout/success/:orderId → OrderSuccessPage        [ProtectedRoute → CUSTOMER]

/orders                    → OrderListPage           [ProtectedRoute → CUSTOMER]
/orders/:orderId           → OrderDetailPage         [ProtectedRoute → CUSTOMER]

/account                   → ProfilePage             [ProtectedRoute → CUSTOMER]
/account/addresses         → AddressBookPage         [ProtectedRoute → CUSTOMER]
/account/wishlist          → WishlistPage            [ProtectedRoute → CUSTOMER]

/admin                     → AdminDashboard          [AdminRoute → ADMIN only]
/admin/products            → ProductListAdmin        [AdminRoute → ADMIN only]
/admin/products/new        → ProductEditor           [AdminRoute → ADMIN only]
/admin/products/:id/edit   → ProductEditor           [AdminRoute → ADMIN only]
/admin/orders              → OrderListAdmin          [AdminRoute → ADMIN only]
/admin/orders/:id          → OrderDetailAdmin        [AdminRoute → ADMIN only]
/admin/users               → UserListAdmin           [AdminRoute → ADMIN only]

/403                       → ForbiddenPage           [Public]
/404                       → NotFoundPage            [Public]
*                          → NotFoundPage            [Public]
```

---

## Layout Structure

```
AppProviders
  └── RouterProvider
        ├── RootLayout (Navbar + Footer + AnnouncementBar)
        │     ├── HomePage
        │     ├── ProductListingPage
        │     ├── ProductDetailPage
        │     └── ...all public pages
        │
        ├── CheckoutLayout (Logo only — no Navbar/Footer)
        │     ├── CheckoutPage
        │     └── OrderSuccessPage
        │
        ├── AccountLayout (Navbar + sidebar nav)
        │     ├── ProfilePage
        │     ├── AddressBookPage
        │     └── WishlistPage
        │
        └── AdminLayout (Admin sidebar + top bar — no public Navbar)
              ├── AdminDashboard
              ├── ProductListAdmin
              └── ...all /admin pages
```

**CheckoutLayout** intentionally removes the Navbar to minimize distraction and reduce cart abandonment — this matches the design system doc recommendation.

---

## Code Splitting (Lazy Loading)

Every page-level component is lazy loaded. Only the shell (`RootLayout`, `Navbar`) loads eagerly.

```typescript
// router/index.tsx
import { lazy, Suspense } from 'react';

const ProductListingPage = lazy(() => import('@/features/catalog/pages/ProductListingPage'));
const ProductDetailPage  = lazy(() => import('@/features/catalog/pages/ProductDetailPage'));
const CheckoutPage       = lazy(() => import('@/features/checkout/pages/CheckoutPage'));
const AdminDashboard     = lazy(() => import('@/features/admin/pages/AdminDashboard'));
// ...every page is lazy

// Wrapped in <Suspense fallback={<PageSkeleton />}>
```

---

## ProtectedRoute Logic

```typescript
// router/ProtectedRoute.tsx
const ProtectedRoute = ({ children }: { children: ReactNode }) => {
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();
  
  if (!isAuthenticated) {
    return <Navigate to={`/login?redirect=${location.pathname}`} replace />;
  }
  return <>{children}</>;
};
```

---

## AdminRoute Logic

```typescript
// router/AdminRoute.tsx
const AdminRoute = ({ children }: { children: ReactNode }) => {
  const { user, isAuthenticated } = useAuthStore();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  if (user?.role !== 'ADMIN') {
    return <Navigate to="/403" replace />; // Never 404 — that leaks route existence
  }
  return <>{children}</>;
};
```

---

## Query String Conventions (Search/Filter)

Search state lives in the URL, not component state. This enables:
- Shareable filtered links
- Browser back/forward navigation for filters
- Server-side rendering compatibility (future)

```
/products?q=acid+wash&color=Red,Black&size=M,L&minPrice=500&maxPrice=2000&sort=popular&page=2
```

| Param | Type | Example |
|---|---|---|
| `q` | string | `acid+wash` |
| `color` | comma-separated | `Red,Black` |
| `size` | comma-separated | `M,L,XL` |
| `minPrice` | number | `500` |
| `maxPrice` | number | `2000` |
| `sort` | enum | `popular`, `newest`, `price_asc`, `price_desc` |
| `page` | number | `2` (for infinite scroll: unused) |
| `categorySlug` | string | `oversized-tees` |
