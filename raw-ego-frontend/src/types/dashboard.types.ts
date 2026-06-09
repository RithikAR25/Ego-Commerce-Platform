/**
 * dashboard.types.ts
 *
 * TypeScript mirror of DashboardSummaryResponse.java and RecentOrderRow.java.
 * All field names match backend DTO fields exactly (camelCase — Jackson serializes as-is).
 */

/**
 * Lightweight projection of a single order used in the dashboard "Recent Orders" panel.
 * Mirrors: com.ego.raw_ego.admin.dashboard.dto.RecentOrderRow
 */
export interface RecentOrderRow {
  id: number;
  status: string;
  grandTotal: number;
  createdAt: string; // ISO-8601 string (Jackson serializes Instant as ISO string)
}

/**
 * Full dashboard KPI summary returned by GET /api/v1/admin/dashboard/summary.
 * Mirrors: com.ego.raw_ego.admin.dashboard.dto.DashboardSummaryResponse
 */
export interface DashboardSummary {
  totalRevenue: number;
  totalOrders: number;
  pendingOrders: number;
  deliveredOrders: number;
  /** Count of return_requests in REQUESTED status (pending admin review). */
  refundCount: number;
  /** Count of inventory records in low-stock state. */
  lowStockCount: number;
  /** Last 10 orders by creation time — lightweight projection. */
  recentOrders: RecentOrderRow[];
  /** Per-status order counts keyed by OrderStatus name. E.g. { "DELIVERED": 88 } */
  ordersByStatus: Record<string, number>;
  /**
   * 7-element array of daily revenue totals (oldest → newest).
   * Index 0 = 6 days ago, index 6 = today.
   */
  revenueTrend: number[];
}
