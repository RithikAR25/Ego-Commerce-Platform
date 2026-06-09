# Admin Portal

## What

A dedicated admin dashboard accessible at `/admin` routes. Provides KPI overview, product management, order management, user management, coupon management, and a manual ES reindex trigger.

> ⚠️ All admin data comes from the same REST API as the storefront — no separate admin API server.

## Access Control

- All `/api/v1/admin/**` endpoints require `ROLE_ADMIN`
- Frontend: `AdminRoute` component (wraps `ProtectedRoute`) — redirects non-admins to `/`
- Layout: `AdminLayout` — sidebar navigation, no storefront navbar/footer

**Default admin credentials (dev/seed):**
```
email:    admin@ego.com
password: Admin@123
```

## Frontend Structure

**Layout:** `src/components/layout/AdminLayout.tsx` — collapsible sidebar

**Admin pages (verified from route structure):**

| Route | Page | Description |
|---|---|---|
| `/admin` | `AdminDashboardPage.tsx` | KPI metrics + "Reindex Search" button |
| `/admin/products` | `AdminProductsPage.tsx` | Product list (all statuses), create button |
| `/admin/products/:id` | `AdminProductDetailPage.tsx` | Full editor — details, variants, images, inventory |
| `/admin/orders` | `AdminOrdersPage.tsx` | Order list with status filters |
| `/admin/orders/:id` | `AdminOrderDetailPage.tsx` | Order detail + status transition |
| `/admin/users` | `AdminUsersPage.tsx` | User list — view, activate/deactivate |
| `/admin/coupons` | `AdminCouponsPage.tsx` | Coupon CRUD |
| `/admin/returns` | `AdminReturnsPage.tsx` | Return request list — approve/reject |

## Dashboard KPIs

**`GET /api/v1/admin/dashboard`** returns:
```json
{
  "totalOrders": 142,
  "totalRevenue": 189450.00,
  "totalCustomers": 87,
  "totalProducts": 12,
  "recentOrders": [...],
  "ordersByStatus": {
    "PENDING_PAYMENT": 3, "CONFIRMED": 8, "PROCESSING": 5,
    "SHIPPED": 12, "OUT_FOR_DELIVERY": 4, "DELIVERED": 98,
    "CANCELLED": 7, "REFUNDED": 5
  }
}
```

## Key Admin Workflows

### Order Status Transitions (Admin)
```
Admin opens order → selects new status from dropdown
→ PUT /api/v1/admin/orders/{id}/status { "status": "SHIPPED" }
→ Backend validates transition (only valid paths allowed)
→ If SHIPPED: fires OrderShippedEvent → shipping email
→ If DELIVERED: fires OrderDeliveredEvent → delivery email + return reminder
```

### Product Publication Flow
```
Admin creates product (DRAFT)
→ Adds variants with prices + EAV attributes
→ Uploads images to Cloudinary
→ Sets inventory quantities
→ Changes status: DRAFT → ACTIVE
→ OutboxPoller (5s delay) → product indexed in ES
```

### Return Processing
```
Admin opens REQUESTED return
→ Reviews customer reason + photos
→ Approves → PUT /api/v1/admin/returns/{id}/approve
→ Backend calls Razorpay refund API (outside @Transactional)
→ Return → REFUND_COMPLETED, Order → REFUNDED, inventory restored
```
or:
```
Admin rejects → PUT /api/v1/admin/returns/{id}/reject
→ Return → REJECTED (customer notified by email)
```

## Admin-Only Endpoints Summary

| Scope | Prefix | Examples |
|---|---|---|
| Products | `/api/v1/admin/products` | CRUD, status, images, variants |
| Orders | `/api/v1/admin/orders` | List, detail, status update |
| Users | `/api/v1/admin/users` | List, activate/deactivate |
| Coupons | `/api/v1/admin/coupons` | CRUD |
| Returns | `/api/v1/admin/returns` | List, approve, reject |
| Inventory | `/api/v1/admin/inventory` | View + adjust quantities |
| Reviews | `/api/v1/admin/reviews` | Delete review |
| Dashboard | `/api/v1/admin/dashboard` | KPIs |
| Search | `/api/v1/admin/search/reindex` | Trigger full ES reindex |
| Categories | `/api/v1/admin/categories` | Create/update/link hierarchy |

## Source References

- `raw-ego-frontend/src/features/dashboard/admin/pages/`
- `raw-ego-frontend/src/components/layout/AdminLayout.tsx`
- `docs/12-agents/feature-context.md` §Admin Portal
