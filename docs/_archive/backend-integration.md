# Backend ↔ Frontend Integration Map — EGO Platform

> This document maps every backend API endpoint and data structure to the frontend component, hook, and type that consumes it.

---

## Auth Module

| Backend | Method | Frontend Hook | Frontend Component |
|---|---|---|---|
| `POST /api/v1/auth/register` | `register()` in `auth.api.ts` | `useRegister` mutation | `RegisterForm.tsx` |
| `POST /api/v1/auth/login` | `login()` | `useLogin` mutation | `LoginForm.tsx` |
| `POST /api/v1/auth/refresh` | Axios interceptor | `api/client.ts` | Auto (transparent) |
| `POST /api/v1/auth/logout` | `logout()` | `useLogout` mutation | Navbar user menu |
| `GET /api/v1/auth/me` | `getMe()` | `useQuery(authKeys.me())` | `AccountPage`, Navbar avatar |

### DTO Mapping

| Backend DTO | Frontend Type | Zod Schema |
|---|---|---|
| `RegisterRequest` | `RegisterRequest` | `auth.schema.ts: RegisterSchema` |
| `LoginRequest` | `LoginRequest` | `auth.schema.ts: LoginSchema` |
| `AuthResponse` | `AuthResponse` | — (response, not form input) |
| `UserResponse` | `UserResponse` | — |

---

## Catalog / Products

| Backend | Method | Frontend Hook | Frontend Component |
|---|---|---|---|
| `GET /api/v1/products/search?q=&color=&size=&...` | `searchProducts()` | `useProductSearch(params)` | `ProductListingPage`, `FilterSidebar` |
| `GET /api/v1/products/:slug` | `getProductBySlug()` | `useProductDetail(slug)` | `ProductDetailPage` |
| `GET /api/v1/products/featured` | `getFeatured()` | `useQuery(productKeys.featured())` | `HomePage` featured section |
| `GET /api/v1/categories` | `getCategories()` | `useQuery(['categories'])` | `Navbar`, `FilterSidebar` |

### Variant System Integration

Backend returns from `GET /api/v1/products/:slug`:
```json
{
  "product": { "id": 1, "name": "...", "basePrice": 999 },
  "variants": [
    {
      "id": 10, "sku": "OAT-RED-M",
      "attributeValues": [
        { "type": "color", "value": "Red", "hexColor": "#FF0000" },
        { "type": "size",  "value": "M" }
      ],
      "inventory": { "qtyAvailable": 8, "qtyReserved": 2 }
    }
  ],
  "images": [
    { "cloudinaryId": "products/1/abc123", "isPrimary": true }
  ]
}
```

Frontend `utils/variant.ts` builds:
```typescript
// AvailabilityMatrix: Color → Size → { variantId, sellable }
// sellable = qtyAvailable - qtyReserved
type AvailabilityMatrix = Map<string, Map<string, { variantId: number; inStock: boolean }>>;
```

`VariantSelector.tsx` reads the matrix to:
- Render color swatches using `hexColor`
- Grey out sizes with `inStock: false` for the selected color
- On both selected → emit `selectedVariantId` to parent

---

## Cart

| Backend | Method | Frontend Hook | Frontend Component |
|---|---|---|---|
| `GET /api/v1/cart` | `getCart()` | `useCart()` | `CartDrawer.tsx` |
| `POST /api/v1/cart/items` | `addCartItem()` | `useAddCartItem()` | ProductDetail "Add to Cart" button |
| `PUT /api/v1/cart/items/:id` | `updateCartItem()` | `useUpdateCartItem()` | `CartItem.tsx` quantity +/- |
| `DELETE /api/v1/cart/items/:id` | `removeCartItem()` | `useRemoveCartItem()` | `CartItem.tsx` remove icon |

---

## Checkout & Orders

| Backend | Method | Frontend Hook | Frontend Component |
|---|---|---|---|
| `POST /api/v1/orders` | `placeOrder()` | `useCheckout()` | `CheckoutPage` "Place Order" button |
| `GET /api/v1/orders` | `getOrders()` | `useOrders()` | `OrderListPage` |
| `GET /api/v1/orders/:id` | `getOrderById()` | `useOrderDetail(id)` | `OrderDetailPage` |
| `POST /api/v1/orders/:id/return` | `requestReturn()` | `useReturnRequest()` | `ReturnRequestModal.tsx` |

### Razorpay Checkout Integration

```
Backend POST /api/v1/orders response includes:
{
  "orderId": "uuid-...",
  "razorpayOrderId": "order_XYZ",
  "amount": 129900,          ← in paise (₹1299)
  "currency": "INR"
}

Frontend utils/razorpay.ts opens the Razorpay modal with these values.
After payment, frontend polls GET /api/v1/orders/:id until status != 'pending_payment'
```

---

## Cloudinary URL Builder

Backend stores `cloudinary_id = "products/42/abc123xyz"`. Frontend builds URLs:

```typescript
// utils/cloudinary.ts

// Card (4:5 ratio) → 400×500
productCardImage("products/42/abc123")
→ "https://res.cloudinary.com/{cloud}/image/upload/c_fill,w_400,h_500,g_auto,q_auto,f_auto/products/42/abc123"

// Hero (PDP) → 800×1000
productHeroImage(id) → c_fill,w_800,h_1000 ...

// Cart thumbnail → 200×250
cartThumbnail(id)    → c_fill,w_200,h_250 ...
```

---

## Admin Module

| Backend | Method | Frontend Hook | Frontend Component |
|---|---|---|---|
| `GET /api/v1/admin/stats` | `getDashboardStats()` | `useAdminStats()` | `AdminDashboard.tsx` |
| `POST /api/v1/admin/products` | `createProduct()` | `useCreateProduct()` | `ProductEditor.tsx` |
| `PUT /api/v1/admin/products/:id` | `updateProduct()` | `useUpdateProduct()` | `ProductEditor.tsx` |
| `POST /api/v1/admin/products/:id/images` | `uploadImage()` | `useUploadImage()` | `ImageUploader.tsx` |
| `GET /api/v1/admin/orders` | `getAdminOrders()` | `useAdminOrders()` | `OrderListAdmin.tsx` |
| `PATCH /api/v1/admin/orders/:id/status` | `updateOrderStatus()` | `useUpdateOrderStatus()` | `OrderDetailAdmin.tsx` |

---

## Order Status → Frontend Display Map

Backend ENUM value → Frontend label and color:

| Backend Status | Label | Color |
|---|---|---|
| `pending_payment` | Awaiting Payment | Amber |
| `payment_failed` | Payment Failed | Red |
| `confirmed` | Order Confirmed | Blue |
| `processing` | Processing | Blue |
| `shipped` | Shipped | Purple |
| `out_for_delivery` | Out for Delivery | Orange |
| `delivered` | Delivered | Green |
| `cancelled` | Cancelled | Grey |
| `return_requested` | Return Requested | Orange |
| `return_approved` | Return Approved | Blue |
| `return_rejected` | Return Rejected | Red |
| `returned` | Returned | Grey |
| `refund_initiated` | Refund Initiated | Blue |
| `refund_completed` | Refund Completed | Green |

This mapping lives in `types/order.types.ts` as a constant record.

---

## Error Code → Frontend Behavior Map

| HTTP Status | Backend Cause | Frontend Action |
|---|---|---|
| 400 + `errors` map | @Valid failure | Map field errors to form via `setError()` |
| 401 (auth endpoint) | Wrong credentials | Show inline form error |
| 401 (resource endpoint) | Expired AT | Axios interceptor → refresh → retry |
| 401 (refresh endpoint) | Expired/revoked RT | clearAuth() + redirect to login |
| 403 | Role insufficient | Navigate to /403 |
| 404 | Resource not found | Show 404 page or empty state |
| 409 | Email already exists, inventory conflict | Toast with backend message |
| 500 | Server error | Toast "Something went wrong. Try again." |
