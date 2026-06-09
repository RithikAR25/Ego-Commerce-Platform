# Component System — EGO Platform Frontend

---

## Architecture Principles

1. **Shared components are stateless.** No API calls inside `components/`. Data is passed via props.
2. **Feature components can have state.** `features/catalog/components/` can call hooks.
3. **No prop drilling past 2 levels.** Beyond 2 levels: use TanStack Query or Zustand.
4. **Every component is typed.** No implicit `any`. All props have explicit interfaces.

---

## Layout Components (`components/layout/`)

### `Navbar.tsx`
- Desktop: Transparent → frosted glass on scroll (backdrop-filter)
- Mobile: Hamburger (MUI Drawer) + centered logo + search + cart badge
- Announcement bar with scrolling marquee above the navbar
- Cart badge reads from `cartStore.count` (Zustand — optimistic)
- Search input expands on focus (Framer Motion width animation)

### `CartDrawer.tsx`
- `MUI Drawer` anchored right, width 420px (desktop) / 100vw (mobile)
- "Your Bag (n)" header
- Free shipping progress bar
- Item list using `CartItem` components
- Sticky footer: subtotal + "PROCEED TO CHECKOUT" CTA
- Reads from `useCart()` TanStack Query hook

### `PageWrapper.tsx`
- Applies max-width (1440px), horizontal padding per breakpoint
- Used by every page inside `RootLayout`

---

## Product Components (`components/product/`)

### `ProductCard.tsx`
```
Props: { product: ProductSummary; onWishlistToggle?: () => void }

Behavior:
- Portrait 4:5 aspect ratio
- Hover: swap primary image → secondary image (Framer Motion opacity crossfade)
- Hover: QuickAddPanel slides up from bottom
- No shadow (elevation=0) — border on hover instead
- Rating stars (compact, 1 line)
- Wishlist heart icon (top-right corner, toggles on click)
```

### `VariantSelector.tsx`
```
Props: { variants: VariantDetail[]; onVariantSelect: (variantId: number) => void }

State:
- selectedColor: string | null
- selectedSize:  string | null

Computed (useMemo):
- availabilityMatrix: AvailabilityMatrix (from utils/variant.ts)
- availableSizesForColor: sizes available for current color selection

Behavior:
- Color swatches: hex circles (or swatch images for patterns)
- Size grid: square buttons — disabled (greyed) if out of stock for selected color
- When both selected → calls onVariantSelect(variantId)
- "Only X left" label when sellable <= lowStockThreshold
```

### `ImageGallery.tsx`
```
Props: { images: ProductImage[]; activeVariantId?: number }

Behavior:
- Main large image (fetchpriority="high")
- Thumbnail strip below (horizontal scroll on mobile)
- Zoom on hover (CSS transform scale)
- Swipe on mobile (touch events)
- Filters images by activeVariantId when provided
```

### `QuickAddPanel.tsx`
```
Props: { product: ProductSummary }

Behavior:
- Slides up from bottom of ProductCard on hover
- Shows size bubbles (S M L XL) — max 4 most common sizes
- One-click add to cart without going to product page
- If more than 4 sizes or color selection needed → links to product page
```

---

## UI Components (`components/ui/`)

### `EgoButton.tsx`
```typescript
interface EgoButtonProps extends ButtonProps {
  loading?: boolean;
}
// Shows CircularProgress when loading=true
// Framer Motion whileTap scale: 0.97 on click
```

### `SkeletonCard.tsx`
Matches exact layout of `ProductCard` — same height/width, grey animated blocks.

### `Toast.tsx`
Global toast system connected to `uiStore.toasts`. Stacks in top-right corner. Auto-dismiss after 4s.

### `ErrorFallback.tsx`
Used by React `<ErrorBoundary>`. Shows styled error with "Try again" button that calls `resetErrorBoundary()`.

---

## Naming Conventions

| Type | Convention | Example |
|---|---|---|
| Component files | PascalCase | `ProductCard.tsx` |
| Hook files | camelCase with `use` prefix | `useProductSearch.ts` |
| API files | camelCase with `.api.ts` suffix | `catalog.api.ts` |
| Type files | camelCase with `.types.ts` suffix | `product.types.ts` |
| Schema files | camelCase with `.schema.ts` suffix | `auth.schema.ts` |
| Store files | camelCase with `Store.ts` suffix | `authStore.ts` |
| Utility files | camelCase | `cloudinary.ts` |
