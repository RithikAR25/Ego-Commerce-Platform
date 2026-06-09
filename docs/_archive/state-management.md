# State Management Architecture — EGO Platform Frontend

> **Server state:** TanStack Query (React Query v5)  
> **UI state:** Zustand v4  
> **Form state:** React Hook Form + Zod

---

## 1. State Boundary Rules

The most important rule in this codebase:

| What it is | Where it lives | NEVER put it in |
|---|---|---|
| API data (products, orders, cart) | TanStack Query | Zustand, useState |
| UI toggles (drawer, modals, toasts) | Zustand | TanStack Query |
| Token (in-memory) | Zustand (`authStore`) | localStorage, Redux |
| Form input | React Hook Form | Zustand, TanStack Query |
| URL filter state | `useSearchParams()` | useState, Zustand |

Violating these boundaries is the leading cause of stale data, double-renders, and sync bugs in React apps.

---

## 2. TanStack Query Configuration (`providers/AppProviders.tsx`)

```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 2 * 60 * 1000,        // 2 min default stale time
      gcTime:    10 * 60 * 1000,        // 10 min garbage collection
      retry: (count, error) => {
        if (axios.isAxiosError(error) && error.response?.status === 404) return false;
        if (axios.isAxiosError(error) && error.response?.status === 403) return false;
        return count < 2;               // retry max 2x on network errors
      },
      refetchOnWindowFocus: false,      // prevents jarring refetches on tab switch
    },
    mutations: {
      onError: (error) => {
        // Global mutation error handler (toast) — individual mutations can override
        const { message } = extractApiError(error);
        toast.error(message);
      }
    }
  }
});
```

---

## 3. Query Key Factory (`hooks/queryKeys.ts`)

Centralized — prevents magic strings and enables precise invalidation.

```typescript
export const authKeys = {
  me: () => ['auth', 'me'] as const,
};

export const productKeys = {
  all:      ()                   => ['products'] as const,
  search:   (p: SearchParams)    => [...productKeys.all(), 'search', p] as const,
  detail:   (slug: string)       => [...productKeys.all(), 'detail', slug] as const,
  featured: ()                   => [...productKeys.all(), 'featured'] as const,
};

export const cartKeys = {
  all:  () => ['cart'] as const,
  list: () => [...cartKeys.all(), 'list'] as const,
};

export const orderKeys = {
  all:    ()             => ['orders'] as const,
  list:   ()             => [...orderKeys.all(), 'list'] as const,
  detail: (id: string)   => [...orderKeys.all(), 'detail', id] as const,
};

export const wishlistKeys = {
  all:  () => ['wishlist'] as const,
};

export const adminKeys = {
  stats:    ()           => ['admin', 'stats'] as const,
  products: ()           => ['admin', 'products'] as const,
  orders:   ()           => ['admin', 'orders'] as const,
};
```

---

## 4. Per-Domain Query Hooks

### `hooks/useProducts.ts`
```typescript
export const useProductSearch = (params: SearchParams) =>
  useQuery({
    queryKey: productKeys.search(params),
    queryFn:  () => searchProducts(params).then(r => r.data.data),
    staleTime: 30_000,   // Search results: 30s (inventory can change)
    placeholderData: keepPreviousData, // avoids flash while paginating
  });

export const useProductDetail = (slug: string) =>
  useQuery({
    queryKey: productKeys.detail(slug),
    queryFn:  () => getProductBySlug(slug).then(r => r.data.data),
    staleTime: 5 * 60_000, // Product detail: 5 min
  });

export const usePrefetchProductDetail = () => {
  const qc = useQueryClient();
  return (slug: string) =>
    qc.prefetchQuery({
      queryKey: productKeys.detail(slug),
      queryFn:  () => getProductBySlug(slug).then(r => r.data.data),
    });
};
```

### `hooks/useCart.ts`
```typescript
export const useCart = () =>
  useQuery({
    queryKey: cartKeys.list(),
    queryFn:  () => getCart().then(r => r.data.data),
    staleTime: 0,           // Cart: always fresh
    enabled: isAuthenticated,
  });

export const useAddCartItem = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: addCartItem,
    onMutate: async (newItem) => {
      await qc.cancelQueries({ queryKey: cartKeys.all() });
      const prev = qc.getQueryData(cartKeys.list());
      // Optimistic update: add item immediately
      qc.setQueryData(cartKeys.list(), (old: CartResponse) => ({
        ...old,
        items: [...old.items, { ...newItem, id: -1 }], // temp id
        totalItems: old.totalItems + newItem.qty,
      }));
      return { prev };
    },
    onError: (_, __, ctx) => qc.setQueryData(cartKeys.list(), ctx?.prev),
    onSettled: () => qc.invalidateQueries({ queryKey: cartKeys.all() }),
  });
};
```

---

## 5. Zustand Stores

### `store/authStore.ts` — Access token + user profile

```typescript
interface AuthState {
  accessToken: string | null;   // IN-MEMORY ONLY. Never localStorage.
  user: UserResponse | null;
  isAuthenticated: boolean;
  
  setTokens: (at: string, rt: string) => void;
  setUser:   (user: UserResponse) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  user: null,
  isAuthenticated: false,

  setTokens: (at, rt) => {
    localStorage.setItem('ego_rt', rt);            // RT to localStorage
    set({ accessToken: at, isAuthenticated: true });
  },

  setUser: (user) => set({ user }),

  clearAuth: () => {
    localStorage.removeItem('ego_rt');
    set({ accessToken: null, user: null, isAuthenticated: false });
  },
}));
```

### `store/uiStore.ts` — Drawers, modals, toasts

```typescript
interface UiState {
  cartDrawerOpen: boolean;
  toasts: Toast[];
  activeModal: 'size-guide' | 'return-request' | 'address-form' | null;
  
  openCartDrawer:  () => void;
  closeCartDrawer: () => void;
  pushToast:       (t: Toast) => void;
  removeToast:     (id: string) => void;
  openModal:       (m: UiState['activeModal']) => void;
  closeModal:      () => void;
}
```

### `store/cartStore.ts` — Optimistic item count for Navbar badge

```typescript
// This is a lightweight mirror of the total item count.
// It's updated optimistically on add/remove for the Navbar badge.
// The source of truth is TanStack Query's cart data.
interface CartBadgeState {
  count: number;
  setCount: (n: number) => void;
  increment: () => void;
  decrement: () => void;
}
```

---

## 6. Form Architecture (React Hook Form + Zod)

### Schema Pattern (`schemas/auth.schema.ts`)

All Zod schemas **mirror backend DTO validation rules** exactly. If the backend requires password min 8 chars + uppercase + special char, the schema enforces it client-side too.

```typescript
export const RegisterSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(100),
  lastName:  z.string().min(1, 'Last name is required').max(100),
  email:     z.string().email('Invalid email address'),
  password:  z.string()
               .min(8, 'Password must be at least 8 characters')
               .max(72, 'Password too long')           // BCrypt 72-char limit
               .regex(/[A-Z]/, 'Must contain uppercase')
               .regex(/[0-9]/, 'Must contain a number')
               .regex(/[^A-Za-z0-9]/, 'Must contain a special character'),
  phone:     z.string().regex(/^\+?[1-9]\d{9,14}$/, 'Invalid phone number').optional(),
});

export type RegisterRequest = z.infer<typeof RegisterSchema>;
```

### Hook Pattern in Forms

```typescript
const form = useForm<RegisterRequest>({
  resolver: zodResolver(RegisterSchema),
  mode: 'onBlur',  // Validate on blur, not on every keystroke
});

// Map backend field errors to form errors
const mutation = useRegister({
  onError: (error) => {
    const { fieldErrors } = extractApiError(error);
    if (fieldErrors) {
      Object.entries(fieldErrors).forEach(([field, msg]) =>
        form.setError(field as keyof RegisterRequest, { message: msg })
      );
    }
  }
});
```
