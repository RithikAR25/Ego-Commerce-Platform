# Frontend Architecture — EGO Platform

> **Stack:** React 19 · TypeScript · Vite · Material UI · React Router · Axios · TanStack Query · Zustand · Framer Motion · React Hook Form · Zod  
> **Backend:** Spring Boot 4 (Java 21) · Stateless JWT · RBAC (CUSTOMER/ADMIN) · MySQL · Redis · Elasticsearch · Cloudinary · Razorpay

---

## 1. Core Architecture Philosophy

The EGO frontend is built around **three strict state boundaries**:

| Layer | Tool | Scope |
|---|---|---|
| **Server State** | TanStack Query | All API data — products, orders, cart, user profile |
| **UI State** | Zustand | Cart drawer open/close, toast queue, wishlist toggle, modals |
| **Form State** | React Hook Form + Zod | All user input — login, register, checkout, address |

**There is NO overlap between these layers.** API data never lives in Zustand. UI toggle state never goes into TanStack Query. This separation is enforced via linting rules and code review gates.

---

## 2. Feature-Based Architecture

The source tree is organized by **business domain** (feature), not by technical type. Each feature is a self-contained vertical slice.

```
src/
├── features/
│   ├── auth/          ← login, register, logout, protected routes
│   ├── catalog/       ← product listing, search, filters, product detail
│   ├── cart/          ← cart drawer, cart items, quantity management
│   ├── checkout/      ← address selection, order summary, Razorpay modal
│   ├── orders/        ← order history, order detail, tracking
│   ├── account/       ← profile, addresses, wishlist
│   └── admin/         ← admin dashboard, product CRUD, order management
│
├── components/        ← shared, stateless UI components only
│   ├── layout/        ← Navbar, Footer, CartDrawer, PageWrapper
│   ├── ui/            ← Button, Input, Badge, Skeleton, Toast, Modal
│   └── product/       ← ProductCard, VariantSelector, ImageGallery
│
├── api/               ← Axios client + all API functions
│   ├── client.ts      ← Axios instance with JWT interceptor + refresh queue
│   ├── auth.api.ts
│   ├── catalog.api.ts
│   ├── cart.api.ts
│   ├── orders.api.ts
│   └── admin.api.ts
│
├── hooks/             ← TanStack Query wrappers (one per domain)
│   ├── useAuth.ts
│   ├── useProducts.ts
│   ├── useCart.ts
│   ├── useOrders.ts
│   └── useAdmin.ts
│
├── store/             ← Zustand stores (UI state only)
│   ├── authStore.ts   ← access token (in-memory), user profile
│   ├── uiStore.ts     ← cart drawer, modals, toasts
│   └── cartStore.ts   ← optimistic cart item count badge
│
├── schemas/           ← Zod validation schemas (mirror backend DTOs)
│   ├── auth.schema.ts
│   ├── checkout.schema.ts
│   └── address.schema.ts
│
├── types/             ← TypeScript interfaces matching API response DTOs
│   ├── auth.types.ts
│   ├── product.types.ts
│   ├── order.types.ts
│   └── api.types.ts   ← ApiResponse<T>, ApiError envelope
│
├── theme/             ← MUI custom theme
│   ├── theme.ts
│   ├── typography.ts
│   └── palette.ts
│
├── utils/             ← Pure utility functions
│   ├── cloudinary.ts  ← URL builder (publicId → CDN URL with transforms)
│   ├── razorpay.ts    ← Razorpay checkout.js loader + modal helper
│   ├── currency.ts    ← ₹ formatting
│   └── variant.ts     ← Availability matrix builder for VariantSelector
│
├── router/
│   ├── index.tsx      ← Route definitions
│   ├── ProtectedRoute.tsx
│   └── AdminRoute.tsx
│
├── providers/
│   └── AppProviders.tsx  ← QueryClientProvider, ThemeProvider, RouterProvider
│
├── App.tsx
└── main.tsx
```

---

## 3. Authentication Architecture

### Token Storage Strategy
Following the backend's security documentation recommendation:

| Token | Storage | Rationale |
|---|---|---|
| **Access Token (AT)** | **Zustand in-memory** (`authStore.ts`) | Never in `localStorage` — XSS would expose it |
| **Refresh Token (RT)** | **`localStorage` with SHA-256 key** | Acceptable trade-off; RT alone cannot authenticate without AT |

### AT + RT Lifecycle in `authStore.ts`
```typescript
interface AuthState {
  accessToken: string | null;   // in-memory — clears on tab close
  user: UserResponse | null;
  isAuthenticated: boolean;
  setTokens: (at: string, rt: string) => void;
  clearAuth: () => void;
}
```

On **app boot** (`main.tsx`): If `localStorage` has a valid refresh token → call `/auth/refresh` silently → store new AT in memory. If that fails → clear and show login.

### Refresh Queue Strategy (from existing doc)
The Axios interceptor in `api/client.ts` implements the **queue pattern** to handle concurrent 401s:

```typescript
let isRefreshing = false;
let failedQueue: Array<{ resolve: Function; reject: Function }> = [];

// When a 401 hits:
// 1. If not refreshing → start refresh, set isRefreshing = true
// 2. If already refreshing → push to queue
// 3. On refresh success → drain queue with new token, retry all
// 4. On refresh failure → drain queue with rejection, call logout()
```

This prevents the "thundering herd" problem where 5 simultaneous API calls all try to refresh independently.

---

## 4. Protected Route Strategy

```
/                   → Public (anyone)
/login              → Public (redirect to / if already authed)
/register           → Public (redirect to / if already authed)
/products/**        → Public
/product/:slug      → Public
/cart               → ProtectedRoute (CUSTOMER)
/checkout           → ProtectedRoute (CUSTOMER)
/orders/**          → ProtectedRoute (CUSTOMER)
/account/**         → ProtectedRoute (CUSTOMER)
/admin/**           → AdminRoute (ADMIN only)
```

**`ProtectedRoute.tsx`:** Checks `authStore.isAuthenticated`. If false, redirects to `/login?redirect={currentPath}`. On successful login, navigates back to the redirect path.

**`AdminRoute.tsx`:** Checks `authStore.user?.role === 'ADMIN'`. If CUSTOMER tries to access admin, shows 403 page — does not leak admin routes exist.

---

## 5. API Architecture

### Base Response Envelope
Matches the backend's `ApiResponse<T>`:
```typescript
// types/api.types.ts
interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

interface ApiError {
  success: false;
  message: string;
  errors?: Record<string, string>; // field-level validation errors
  timestamp: string;
}
```

### Axios Client (`api/client.ts`)
```
Request interceptor  → attach Authorization: Bearer {accessToken}
Response interceptor → on 401: refresh queue → retry or logout
                     → on 403: navigate to /403
                     → on 5xx: toast "Something went wrong"
```

### API Functions (per domain)
Each domain file exports typed async functions — no raw `axios.get` calls outside `api/`:

```typescript
// api/catalog.api.ts
export const searchProducts = (params: SearchParams): Promise<FacetedSearchResponse> => ...
export const getProductBySlug = (slug: string): Promise<ProductDetailResponse> => ...
export const getCategories = (): Promise<Category[]> => ...
```

---

## 6. TanStack Query Architecture

### Query Key Factory
Centralized key factories prevent key collisions and enable precise invalidation:

```typescript
// hooks/queryKeys.ts
export const productKeys = {
  all: ['products'] as const,
  search: (params: SearchParams) => [...productKeys.all, 'search', params] as const,
  detail: (slug: string) => [...productKeys.all, 'detail', slug] as const,
  featured: () => [...productKeys.all, 'featured'] as const,
};

export const orderKeys = {
  all: ['orders'] as const,
  list: () => [...orderKeys.all, 'list'] as const,
  detail: (id: string) => [...orderKeys.all, 'detail', id] as const,
};
```

### Caching Strategy

| Query | staleTime | gcTime | Notes |
|---|---|---|---|
| Product detail | 5 min | 30 min | Products rarely change mid-session |
| Product search | 30s | 5 min | Facets change with inventory updates |
| Categories | 30 min | 1 hour | Very stable |
| Cart | 0 (always fresh) | 5 min | Must always reflect real inventory |
| Orders | 2 min | 10 min | Order status can update |
| User profile | 5 min | 30 min | — |

### Optimistic Updates (Cart)
When a user taps "Add to Cart", the UI responds instantly. If the API fails, TanStack Query rolls back automatically:

```typescript
// hooks/useCart.ts
useMutation({
  mutationFn: addCartItem,
  onMutate: async (newItem) => {
    await queryClient.cancelQueries({ queryKey: cartKeys.all });
    const prev = queryClient.getQueryData(cartKeys.list());
    queryClient.setQueryData(cartKeys.list(), old => [...old, newItem]);
    return { prev };
  },
  onError: (err, _, ctx) => {
    queryClient.setQueryData(cartKeys.list(), ctx?.prev);
  },
  onSettled: () => queryClient.invalidateQueries({ queryKey: cartKeys.all })
})
```

---

## 7. Product Variant Selector Architecture

This is the most complex UI component. It maps directly to the backend's EAV schema.

### Availability Matrix Builder (`utils/variant.ts`)
```typescript
// From backend: variants = [{ id, sku, attributeValues: [{type:'color', value:'Red'}, {type:'size', value:'M'}], inventory }]
// Build: Map<colorValue, Map<sizeValue, { variantId, inStock }>>

type AvailabilityMatrix = Map<string, Map<string, { variantId: number; inStock: boolean }>>;
```

### VariantSelector Logic
1. User lands on product page → fetch all variants + inventory
2. User selects **Color** → sizes available for that color are shown; sizes with 0 stock are greyed out
3. User selects **Size** → resolves to exactly one `variantId`
4. `variantId` is passed to "Add to Cart" mutation
5. If a combination has 0 stock: button shows "Out of Stock" — disabled

---

## 8. Cloudinary Image Strategy (`utils/cloudinary.ts`)

Backend stores only `cloudinary_id` (e.g. `products/42/abc123`). Frontend builds URLs:

```typescript
const CLOUD_NAME = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME;
const BASE = `https://res.cloudinary.com/${CLOUD_NAME}/image/upload`;

export const productCardImage = (id: string) =>
  `${BASE}/c_fill,w_400,h_500,g_auto,q_auto,f_auto/${id}`;

export const productHeroImage = (id: string) =>
  `${BASE}/c_fill,w_800,h_1000,g_auto,q_auto,f_auto/${id}`;

export const cartThumbnail = (id: string) =>
  `${BASE}/c_fill,w_200,h_250,g_auto,q_auto,f_auto/${id}`;
```

The `f_auto` parameter means Cloudinary serves WebP to supported browsers automatically.

---

## 9. Razorpay Checkout Flow (`utils/razorpay.ts`)

```
1. User clicks "Place Order" →
2. Frontend calls POST /api/v1/orders (backend: lock inventory → create Razorpay order)
3. Backend returns { razorpayOrderId, amount, currency }
4. Frontend loads Razorpay checkout.js modal
5. User pays
6. Razorpay fires webhook to backend → backend verifies → order status: CONFIRMED
7. Frontend polls GET /api/v1/orders/:id every 3s (max 30s) to confirm status change
8. On CONFIRMED → navigate to /orders/:id (success page)
9. On timeout → show "Payment processing, check your orders page"
```

**Never trust the frontend success callback as order confirmation.** Always drive from the backend webhook (per Razorpay integration doc).

---

## 10. Search & Filter Architecture

Maps directly to the Elasticsearch `FacetedSearchResponse`:

```typescript
// Search state lives in URL params (not component state) — enables shareable links
// ?q=acid+wash&color=Red&size=M&minPrice=500&sort=popular&page=1

// useSearchParams() from React Router drives the TanStack Query key
const [searchParams, setSearchParams] = useSearchParams();
const query = useProducts({ ...parseSearchParams(searchParams) });
```

**Facets** (size/color filter chips) come from the ES aggregations in the response, not hardcoded. The frontend renders whatever the backend returns — adding a new attribute type requires no frontend code change.

---

## 11. Error Handling Architecture

### Levels of Error Handling

| Level | Handler | Output |
|---|---|---|
| Field validation (401/400 with `errors` map) | React Hook Form `setError` | Inline field error |
| Business errors (4xx `message`) | Toast notification | Top-right toast |
| Auth failure (401 unrecoverable) | Axios interceptor → logout | Redirect to login |
| Authorization failure (403) | Axios interceptor | Navigate to /403 |
| Server error (500) | Axios interceptor | Toast "Something went wrong" |
| Boundary error (unhandled throw) | React `ErrorBoundary` | Full-page error fallback |

---

## 12. Performance Strategy

- **Route-based code splitting:** Every page-level component is `lazy()` loaded
- **Infinite scroll:** Product listing uses TanStack Query `useInfiniteQuery`
- **Image lazy loading:** All product images use `loading="lazy"` + Cloudinary CDN
- **Skeleton loaders:** TanStack Query `isLoading` state drives skeleton components — no spinners
- **Query prefetching:** Hover over ProductCard → prefetch `getProductBySlug` so detail page loads instantly
- **Stale-while-revalidate:** TanStack Query serves cached data while refreshing in background

---

## 13. Admin Frontend Architecture

Admin is a fully separate route subtree (`/admin/**`) behind `AdminRoute`. It shares the same Axios client, theme, and component library — but has its own pages, hooks, and API functions.

```
features/admin/
├── pages/
│   ├── AdminDashboard.tsx    ← stats widgets (revenue, orders, users)
│   ├── ProductList.tsx       ← searchable table with CRUD actions
│   ├── ProductEditor.tsx     ← create/edit product + image upload
│   ├── OrderList.tsx         ← filterable order list with status change
│   ├── OrderDetail.tsx       ← full order + status machine controls
│   └── UserList.tsx          ← user management (activate/deactivate)
├── hooks/
│   ├── useAdminProducts.ts
│   └── useAdminOrders.ts
└── components/
    ├── StatusBadge.tsx        ← color-coded order status pill
    ├── InventoryEditor.tsx    ← inline qty_available editor
    └── ImageUploader.tsx      ← Cloudinary upload via backend API
```
