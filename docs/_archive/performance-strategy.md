# Performance Strategy — EGO Platform Frontend

---

## 1. Route-Level Code Splitting

Every page component is dynamically imported. Only the shell (`Navbar`, `AppProviders`) loads eagerly.

```typescript
// ~200KB initial JS bundle target
// Each page: ~20–60KB on demand
const ProductDetailPage = lazy(() => import('@/features/catalog/pages/ProductDetailPage'));
```

---

## 2. Query Prefetching on Hover

When a user hovers over a `ProductCard` for >150ms, the product detail data is prefetched. By the time they click, the page feels instant.

```typescript
const prefetch = usePrefetchProductDetail();
<ProductCard onMouseEnter={() => prefetch(product.slug)} />
```

---

## 3. Infinite Scroll (Product Listing)

Uses TanStack Query's `useInfiniteQuery`. No "Load More" button — auto-triggers at 80% scroll depth via `IntersectionObserver`.

```typescript
const { data, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
  queryKey: productKeys.search(params),
  queryFn: ({ pageParam = 0 }) => searchProducts({ ...params, page: pageParam }),
  getNextPageParam: (last) => last.data.data.last ? undefined : last.data.data.page + 1,
  initialPageParam: 0,
});
```

---

## 4. Cloudinary Image Optimization

- **`f_auto`:** Cloudinary serves WebP to Chrome/Edge, JPEG to Safari — 25-40% size reduction
- **`q_auto`:** Adaptive quality per image content
- **`loading="lazy"`:** Native browser lazy loading on all non-LCP images
- **`fetchpriority="high"`:** Set on the hero/primary product image (LCP element)

```typescript
// ProductCard: small thumbnail, lazy
<img src={productCardImage(cloudinaryId)} loading="lazy" />

// ProductDetailPage hero: priority load
<img src={productHeroImage(cloudinaryId)} fetchPriority="high" />
```

---

## 5. Skeleton Loaders (No Spinners)

Every data-dependent section has a skeleton component that matches the exact layout of the loaded content. This prevents layout shift and creates a premium feel.

```typescript
// In ProductListingPage:
if (isLoading) return <ProductGridSkeleton count={12} />;
```

TanStack Query's `placeholderData: keepPreviousData` keeps the old results visible while new search results load — no flash between filter changes.

---

## 6. Stale-While-Revalidate

TanStack Query serves cached data immediately while refreshing in the background. Users always see content — never a blank loading state on repeat visits.

---

## 7. Memoization

```typescript
// Heavy list renders use useMemo / React.memo
const VariantSelector = React.memo(({ variants, onSelect }) => { ... });

// Availability matrix is computed once per variant set
const matrix = useMemo(() => buildAvailabilityMatrix(variants), [variants]);
```

---

## 8. Component System (`components/ui/`)

All shared UI components are pure/stateless. They receive data via props and emit via callbacks. No internal API calls in shared components.

### Reusable Components

| Component | Description | Props |
|---|---|---|
| `EgoButton` | Styled MUI Button + Framer motion tap | `variant`, `loading`, `fullWidth` |
| `EgoInput` | Styled MUI TextField | Forwarded RHF `register` |
| `SkeletonCard` | Product card skeleton | `count` |
| `SkeletonList` | Order/list skeleton | `rows` |
| `Toast` | Animated notification | `message`, `severity` |
| `ErrorFallback` | React ErrorBoundary fallback | `error`, `resetErrorBoundary` |
| `EmptyState` | No results / empty cart | `icon`, `title`, `subtitle`, `action` |

---

## 9. Vite Build Optimizations

```typescript
// vite.config.ts
export default defineConfig({
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'mui-vendor':   ['@mui/material', '@mui/icons-material'],
          'query-vendor': ['@tanstack/react-query'],
          'motion':       ['framer-motion'],
        },
      },
    },
    chunkSizeWarningLimit: 500,
  },
});
```

This prevents one large monolithic bundle. Each vendor library is a separate chunk with long-term caching.

---

## 10. Font Loading

```html
<!-- index.html — preconnect before the stylesheet loads -->
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Syne:wght@600;700;800&family=Manrope:wght@400;500;600;700&display=swap" rel="stylesheet">
```

`font-display: swap` (Google Fonts default) ensures text is visible immediately with the fallback font while the custom font loads.
