package com.ego.raw_ego.admin.dashboard.service;

import com.ego.raw_ego.admin.dashboard.dto.DashboardSummaryResponse;

/**
 * Contract for the admin dashboard KPI summary.
 *
 * <p>Aggregates cross-domain metrics (orders, inventory, returns) into a single
 * summary response. All operations are read-only.
 */
public interface DashboardService {

    /**
     * Computes and returns the full dashboard KPI summary.
     *
     * @return assembled {@link DashboardSummaryResponse} — never null
     */
    DashboardSummaryResponse getSummary();
}
