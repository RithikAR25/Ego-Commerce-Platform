/**
 * queryKeys.ts
 *
 * Centralized TanStack Query key factories.
 *
 * Why this pattern:
 *   1. No magic strings — keys are computed, not copied.
 *   2. Precise invalidation: queryClient.invalidateQueries({ queryKey: cartKeys.all() })
 *      invalidates EVERYTHING in the cart domain.
 *   3. Hierarchical: invalidating productKeys.all() also invalidates productKeys.detail('slug')
 *      because it's a prefix match.
 *   4. TypeScript-safe: `as const` gives each key its exact literal type.
 *
 * Naming convention:
 *   all()    → base key for the domain (used for bulk invalidation)
 *   list()   → paginated/filtered lists
 *   detail() → single entity by identifier
 */

export const authKeys = {
  all: () => ['auth'] as const,
  me: () => ['auth', 'me'] as const,
};

export const productKeys = {
  all: () => ['products'] as const,
  list: (p?: object) => ['products', 'list', p ?? {}] as const,
  featured: () => ['products', 'featured'] as const,
  search: (p: object) => ['products', 'search', p] as const,
  detail: (slug: string) => ['products', 'detail', slug] as const,
};

export const categoryKeys = {
  all: () => ['categories'] as const,
  tree: () => ['categories', 'tree'] as const,
};

export const imageKeys = {
  all: () => ['images'] as const,
  gallery: (productId: number) => ['images', 'gallery', productId] as const,
  variant: (productId: number, variantId: number) => ['images', 'variant', productId, variantId] as const,
};

export const cartKeys = {
  all: () => ['cart'] as const,
  list: () => ['cart', 'list'] as const,
};

export const orderKeys = {
  all: () => ['orders'] as const,
  list: () => ['orders', 'list'] as const,
  detail: (id: string) => ['orders', 'detail', id] as const,
};

export const wishlistKeys = {
  all: () => ['wishlist'] as const,
  list: () => ['wishlist', 'list'] as const,
};

export const adminKeys = {
  stats: () => ['admin', 'stats'] as const,
  categories: () => ['admin', 'categories'] as const,
  products: (p?: object) => ['admin', 'products', p ?? {}] as const,
  orders: (f?: object) => ['admin', 'orders', f ?? {}] as const,
  users: () => ['admin', 'users'] as const,
  attributeTypes: (productId: number) => ['admin', 'attributeTypes', productId] as const,
};

