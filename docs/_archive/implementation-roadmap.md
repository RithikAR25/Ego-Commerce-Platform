# EGO Frontend — Implementation Roadmap

---

## Phase 1: Foundation (Week 1)
**Goal:** Running app with correct structure, theme, and auth working end-to-end.

- [ ] Install all dependencies (`npm install` for MUI, React Router, TanStack Query, Zustand, Framer Motion, RHF, Zod, Axios)
- [ ] Create full `src/` folder structure per `folder-structure.md`
- [ ] Configure `vite.config.ts` path aliases (`@/` → `src/`)
- [ ] Implement MUI theme system (`theme/theme.ts`, `palette.ts`, `typography.ts`)
- [ ] Implement `AppProviders.tsx` (QueryClient + ThemeProvider)
- [ ] Implement `api/client.ts` (Axios + refresh interceptor)
- [ ] Implement `store/authStore.ts`
- [ ] Implement `Navbar.tsx` (basic, no cart yet)
- [ ] Implement `LoginPage.tsx` + `LoginForm.tsx` → connect to `POST /auth/login`
- [ ] Implement `RegisterPage.tsx` → connect to `POST /auth/register`
- [ ] Implement `ProtectedRoute.tsx` + `AdminRoute.tsx`
- [ ] Implement router (`router/index.tsx`) with lazy loading
- [ ] Implement boot sequence (silent refresh on app load)
- [ ] **Test:** Register → Login → `/account` (protected) → Logout → redirected

---

## Phase 2: Catalog (Week 2)
**Goal:** Browse products, search, filter — the core shopping experience.

- [ ] Implement `ProductListingPage.tsx` with infinite scroll
- [ ] Implement `FilterSidebar.tsx` (facets from ES aggregations — dynamic, not hardcoded)
- [ ] Implement `ProductCard.tsx` with hover image swap + QuickAddPanel
- [ ] Implement `ProductDetailPage.tsx` skeleton
- [ ] Implement `VariantSelector.tsx` (availability matrix)
- [ ] Implement `ImageGallery.tsx`
- [ ] Implement `utils/cloudinary.ts` URL builders
- [ ] Implement `HomePage.tsx` (hero, categories bento, featured carousel)
- [ ] Connect `useProductSearch`, `useProductDetail` hooks
- [ ] **Test:** Browse → search `?q=tee` → filter by color/size → product page → variant selection

---

## Phase 3: Cart (Week 2–3)
**Goal:** Full cart flow with optimistic updates.

- [ ] Implement `store/cartStore.ts`
- [ ] Implement `CartDrawer.tsx` (right drawer, free shipping progress bar)
- [ ] Implement `CartItem.tsx` (qty +/-, remove)
- [ ] Implement `useCart`, `useAddCartItem`, `useUpdateCartItem`, `useRemoveCartItem`
- [ ] Wire "Add to Cart" button in `ProductDetailPage` → `useAddCartItem()`
- [ ] Wire QuickAdd in `ProductCard`
- [ ] Update Navbar cart badge from `cartStore.count`
- [ ] **Test:** Add item → see cart drawer update instantly → remove item → qty change

---

## Phase 4: Checkout + Orders (Week 3)
**Goal:** Complete purchase flow with Razorpay.

- [ ] Implement `CheckoutPage.tsx` (MUI Stepper: Address → Review → Pay)
- [ ] Implement `AddressStep.tsx` (saved addresses list + new address form)
- [ ] Implement `OrderSummary.tsx` (right panel with line items, totals, coupon input)
- [ ] Implement `utils/razorpay.ts` (script loader + modal opener)
- [ ] Implement `useCheckout.ts` (placeOrder → Razorpay modal → poll order status)
- [ ] Implement `OrderSuccessPage.tsx`
- [ ] Implement `OrderListPage.tsx` + `OrderDetailPage.tsx`
- [ ] Implement `OrderStatusTimeline.tsx`
- [ ] **Test:** Checkout → Razorpay test mode payment → success page → order detail

---

## Phase 5: Account + Wishlist (Week 4)
**Goal:** User account management.

- [ ] Implement `ProfilePage.tsx` (edit name, phone)
- [ ] Implement `AddressBookPage.tsx` (add/edit/delete addresses)
- [ ] Implement `WishlistPage.tsx`
- [ ] Implement `ReturnRequestModal.tsx`
- [ ] **Test:** Edit profile → add address → wishlist a product → request return

---

## Phase 6: Admin Panel (Week 4–5)
**Goal:** Admin routes with product CRUD and order management.

- [ ] Implement `AdminLayout.tsx` (sidebar nav, no public navbar)
- [ ] Implement `AdminDashboard.tsx` (stats cards: revenue, orders, users)
- [ ] Implement `ProductListAdmin.tsx` (searchable data table)
- [ ] Implement `ProductEditor.tsx` (create/edit form + `ImageUploader.tsx`)
- [ ] Implement `OrderListAdmin.tsx` (status filter + search)
- [ ] Implement `OrderDetailAdmin.tsx` (status machine controls)
- [ ] **Test:** Admin login → create product → upload image → manage orders

---

## Phase 7: Polish + Performance (Week 5)
**Goal:** Premium feel, animations, and optimization.

- [ ] Add Framer Motion page transitions (fade-up on enter)
- [ ] Add staggered product grid animation
- [ ] Add announcement bar marquee
- [ ] Add hover prefetch on ProductCard
- [ ] Add Vite `manualChunks` config
- [ ] Add React `ErrorBoundary` wrapping all pages
- [ ] Add skeleton loaders for all loading states
- [ ] Add `<meta>` tags per page for SEO
- [ ] Performance audit: Lighthouse score target > 85 on mobile
- [ ] **Test:** Full user journey on mobile (iPhone screen emulation)
