# API Architecture — EGO Platform Frontend

> **HTTP Client:** Axios  
> **Base URL:** `VITE_API_BASE_URL` (e.g. `http://localhost:8080/api/v1`)  
> **Auth:** Bearer token injected via request interceptor  
> **Refresh:** Queue-based 401 interceptor (see auth-flow.md)

---

## Axios Client (`api/client.ts`)

The single Axios instance used by the entire application. No direct `fetch()` or raw `axios` imports elsewhere.

```typescript
import axios from 'axios';
import { useAuthStore } from '@/store/authStore';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

// ── Request interceptor ─────────────────────────────────────
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Response interceptor + refresh queue ────────────────────
let isRefreshing = false;
let failedQueue: Array<{ resolve: (token: string) => void; reject: (err: unknown) => void }> = [];

const drainQueue = (token: string | null, error: unknown) => {
  failedQueue.forEach(p => token ? p.resolve(token) : p.reject(error));
  failedQueue = [];
};

apiClient.interceptors.response.use(
  response => response,
  async error => {
    const original = error.config;

    if (error.response?.status === 401 && !original._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(token => {
          original.headers.Authorization = `Bearer ${token}`;
          return apiClient(original);
        });
      }

      original._retry = true;
      isRefreshing = true;

      try {
        const rt = localStorage.getItem('ego_rt');
        if (!rt) throw new Error('No refresh token');
        
        const { data } = await apiClient.post('/auth/refresh', { refreshToken: rt });
        const { accessToken, refreshToken } = data.data;
        
        useAuthStore.getState().setTokens(accessToken, refreshToken);
        localStorage.setItem('ego_rt', refreshToken);
        
        drainQueue(accessToken, null);
        original.headers.Authorization = `Bearer ${accessToken}`;
        return apiClient(original);
      } catch (refreshError) {
        drainQueue(null, refreshError);
        useAuthStore.getState().clearAuth();
        localStorage.removeItem('ego_rt');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    if (error.response?.status === 403) {
      window.location.href = '/403';
    }

    return Promise.reject(error);
  }
);

export default apiClient;
```

---

## API Response Envelope

All backend responses follow `ApiResponse<T>`. Frontend always unwraps via `response.data.data`:

```typescript
// types/api.types.ts
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ApiError {
  success: false;
  message: string;
  errors?: Record<string, string>;
  timestamp: string;
}
```

---

## Domain API Files

### `auth.api.ts`
```typescript
export const register  = (body: RegisterRequest) => apiClient.post<ApiResponse<AuthResponse>>('/auth/register', body);
export const login     = (body: LoginRequest)    => apiClient.post<ApiResponse<AuthResponse>>('/auth/login', body);
export const refresh   = (body: { refreshToken: string }) => apiClient.post<ApiResponse<AuthResponse>>('/auth/refresh', body);
export const logout    = (body: { refreshToken: string }) => apiClient.post('/auth/logout', body);
export const getMe     = ()                      => apiClient.get<ApiResponse<UserResponse>>('/auth/me');
```

### `catalog.api.ts`
```typescript
export const searchProducts  = (p: SearchParams) => apiClient.get<ApiResponse<FacetedSearchResponse>>('/products/search', { params: p });
export const getProductBySlug = (slug: string)   => apiClient.get<ApiResponse<ProductDetail>>(`/products/${slug}`);
export const getFeatured     = ()                => apiClient.get<ApiResponse<Product[]>>('/products/featured');
export const getCategories   = ()                => apiClient.get<ApiResponse<Category[]>>('/categories');
```

### `cart.api.ts`
```typescript
export const getCart       = ()                                         => apiClient.get<ApiResponse<CartResponse>>('/cart');
export const addCartItem   = (body: { variantId: number; qty: number }) => apiClient.post<ApiResponse<CartResponse>>('/cart/items', body);
export const updateCartItem = (itemId: number, qty: number)             => apiClient.put<ApiResponse<CartResponse>>(`/cart/items/${itemId}`, { qty });
export const removeCartItem = (itemId: number)                          => apiClient.delete<ApiResponse<CartResponse>>(`/cart/items/${itemId}`);
export const clearCart     = ()                                         => apiClient.delete('/cart');
```

### `orders.api.ts`
```typescript
export const placeOrder    = (body: PlaceOrderRequest) => apiClient.post<ApiResponse<OrderResponse>>('/orders', body);
export const getOrders     = (page = 0)                => apiClient.get<ApiResponse<PaginatedResponse<Order>>>('/orders', { params: { page } });
export const getOrderById  = (id: string)              => apiClient.get<ApiResponse<OrderDetail>>(`/orders/${id}`);
export const requestReturn = (orderId: string, body: ReturnRequest) => apiClient.post(`/orders/${orderId}/return`, body);
```

### `admin.api.ts`
```typescript
export const createProduct   = (body: CreateProductRequest) => apiClient.post<ApiResponse<Product>>('/admin/products', body);
export const updateProduct   = (id: number, body: UpdateProductRequest) => apiClient.put<ApiResponse<Product>>(`/admin/products/${id}`, body);
export const uploadImage     = (productId: number, file: File) => {
  const form = new FormData();
  form.append('file', file);
  return apiClient.post(`/admin/products/${productId}/images`, form, { headers: { 'Content-Type': 'multipart/form-data' } });
};
export const updateOrderStatus = (orderId: string, status: OrderStatus) => apiClient.patch(`/admin/orders/${orderId}/status`, { status });
export const getDashboardStats = () => apiClient.get<ApiResponse<AdminStats>>('/admin/stats');
```

---

## Error Extraction Helper

```typescript
// utils/apiError.ts
export const extractApiError = (error: unknown): { message: string; fieldErrors?: Record<string, string> } => {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiError;
    return {
      message: data?.message ?? 'An unexpected error occurred.',
      fieldErrors: data?.errors,
    };
  }
  return { message: 'Network error. Please check your connection.' };
};
```

Used in mutations:
```typescript
onError: (error) => {
  const { message, fieldErrors } = extractApiError(error);
  if (fieldErrors) {
    Object.entries(fieldErrors).forEach(([field, msg]) => form.setError(field as any, { message: msg }));
  } else {
    toast.error(message);
  }
}
```
