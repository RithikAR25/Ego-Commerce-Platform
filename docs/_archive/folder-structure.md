src/
├── features/
│   │
│   ├── auth/
│   │   ├── pages/
│   │   │   ├── LoginPage.tsx
│   │   │   └── RegisterPage.tsx
│   │   ├── components/
│   │   │   ├── LoginForm.tsx
│   │   │   └── RegisterForm.tsx
│   │   └── hooks/
│   │       └── useAuthMutations.ts
│   │
│   ├── catalog/
│   │   ├── pages/
│   │   │   ├── ProductListingPage.tsx
│   │   │   └── ProductDetailPage.tsx
│   │   ├── components/
│   │   │   ├── FilterSidebar.tsx
│   │   │   ├── FacetChips.tsx
│   │   │   ├── SortDropdown.tsx
│   │   │   ├── ProductGrid.tsx
│   │   │   └── SearchBar.tsx
│   │   └── hooks/
│   │       ├── useProductSearch.ts
│   │       └── useProductDetail.ts
│   │
│   ├── cart/
│   │   ├── components/
│   │   │   ├── CartDrawer.tsx
│   │   │   ├── CartItem.tsx
│   │   │   └── FreeShippingBar.tsx
│   │   └── hooks/
│   │       └── useCartMutations.ts
│   │
│   ├── checkout/
│   │   ├── pages/
│   │   │   ├── CheckoutPage.tsx
│   │   │   └── OrderSuccessPage.tsx
│   │   ├── components/
│   │   │   ├── AddressStep.tsx
│   │   │   ├── OrderSummary.tsx
│   │   │   └── CouponInput.tsx
│   │   └── hooks/
│   │       └── useCheckout.ts
│   │
│   ├── orders/
│   │   ├── pages/
│   │   │   ├── OrderListPage.tsx
│   │   │   └── OrderDetailPage.tsx
│   │   └── components/
│   │       ├── OrderCard.tsx
│   │       ├── OrderStatusTimeline.tsx
│   │       └── ReturnRequestModal.tsx
│   │
│   ├── account/
│   │   ├── pages/
│   │   │   ├── ProfilePage.tsx
│   │   │   ├── AddressBookPage.tsx
│   │   │   └── WishlistPage.tsx
│   │   └── components/
│   │       ├── AddressCard.tsx
│   │       └── ProfileForm.tsx
│   │
│   └── admin/
│       ├── pages/
│       │   ├── AdminDashboard.tsx
│       │   ├── ProductListAdmin.tsx
│       │   ├── ProductEditor.tsx
│       │   ├── OrderListAdmin.tsx
│       │   ├── OrderDetailAdmin.tsx
│       │   └── UserListAdmin.tsx
│       ├── components/
│       │   ├── StatusBadge.tsx
│       │   ├── InventoryEditor.tsx
│       │   └── ImageUploader.tsx
│       └── hooks/
│           ├── useAdminProducts.ts
│           └── useAdminOrders.ts
│
├── components/
│   ├── layout/
│   │   ├── Navbar.tsx
│   │   ├── Footer.tsx
│   │   ├── PageWrapper.tsx
│   │   └── AnnouncementBar.tsx
│   ├── ui/
│   │   ├── EgoButton.tsx
│   │   ├── EgoInput.tsx
│   │   ├── SkeletonCard.tsx
│   │   ├── SkeletonList.tsx
│   │   ├── Toast.tsx
│   │   ├── ErrorFallback.tsx
│   │   └── EmptyState.tsx
│   └── product/
│       ├── ProductCard.tsx
│       ├── VariantSelector.tsx
│       ├── ImageGallery.tsx
│       ├── SizeGuide.tsx
│       └── QuickAddPanel.tsx
│
├── api/
│   ├── client.ts              ← Axios instance + interceptors + refresh queue
│   ├── auth.api.ts
│   ├── catalog.api.ts
│   ├── cart.api.ts
│   ├── orders.api.ts
│   ├── account.api.ts
│   └── admin.api.ts
│
├── hooks/
│   ├── queryKeys.ts           ← Centralized TanStack Query key factories
│   ├── useAuth.ts
│   ├── useProducts.ts
│   ├── useCart.ts
│   ├── useOrders.ts
│   └── useWishlist.ts
│
├── store/
│   ├── authStore.ts           ← accessToken (in-memory), user profile, isAuthenticated
│   ├── uiStore.ts             ← cartDrawerOpen, toasts, activeModal
│   └── cartStore.ts           ← optimistic itemCount for Navbar badge
│
├── schemas/
│   ├── auth.schema.ts         ← LoginSchema, RegisterSchema (mirrors backend DTOs)
│   ├── checkout.schema.ts     ← CheckoutSchema, AddressSchema
│   └── coupon.schema.ts
│
├── types/
│   ├── api.types.ts           ← ApiResponse<T>, ApiError, PaginatedResponse<T>
│   ├── auth.types.ts          ← UserResponse, AuthResponse
│   ├── product.types.ts       ← Product, Variant, AttributeType, InventoryStatus
│   ├── order.types.ts         ← Order, OrderItem, OrderStatus enum
│   └── admin.types.ts         ← AdminStats, AdminOrderFilter
│
├── theme/
│   ├── theme.ts               ← MUI ThemeProvider config (root)
│   ├── palette.ts             ← Color tokens
│   ├── typography.ts          ← Font + scale
│   └── overrides.ts           ← MUI component style overrides
│
├── utils/
│   ├── cloudinary.ts          ← publicId → CDN URL builders
│   ├── razorpay.ts            ← Script loader + modal opener helper
│   ├── currency.ts            ← formatINR(amount): "₹1,299"
│   ├── variant.ts             ← buildAvailabilityMatrix(variants)
│   └── date.ts                ← formatOrderDate, relativeTime
│
├── router/
│   ├── index.tsx              ← createBrowserRouter config
│   ├── ProtectedRoute.tsx     ← isAuthenticated guard
│   └── AdminRoute.tsx         ← ADMIN role guard
│
├── providers/
│   └── AppProviders.tsx       ← QueryClient + Theme + Router wrapped together
│
├── App.tsx
└── main.tsx
