package com.ego.raw_ego.admin.dashboard.controller;

import com.ego.raw_ego.admin.dashboard.dto.DashboardSummaryResponse;
import com.ego.raw_ego.admin.dashboard.service.DashboardService;
import com.ego.raw_ego.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only controller for the dashboard KPI summary endpoint.
 *
 * <pre>
 * GET /api/v1/admin/dashboard/summary
 * </pre>
 *
 * <p>Security: doubly guarded —
 * <ul>
 *   <li>Route-level: {@code /api/v1/admin/**} requires {@code ROLE_ADMIN} in SecurityConfig.</li>
 *   <li>Method-level: {@code @PreAuthorize} provides an explicit additional guard.</li>
 * </ul>
 *
 * <p>This controller is stateless and side-effect-free. All computation
 * happens in {@link DashboardService} which is {@code @Transactional(readOnly = true)}.
 */
@RestController
@Tag(name = "Admin – Dashboard", description = "Admin dashboard KPI aggregation endpoint")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Returns the full admin dashboard KPI summary.
     *
     * <p>Computes and returns:
     * <ul>
     *   <li>Total revenue from confirmed+ orders</li>
     *   <li>Order counts (total, pending, delivered)</li>
     *   <li>Pending return request count</li>
     *   <li>Low-stock inventory alert count</li>
     *   <li>Last 10 recent orders (lightweight projection)</li>
     *   <li>Order counts per status for chart rendering</li>
     *   <li>7-day daily revenue trend</li>
     * </ul>
     *
     * <p>All aggregations are single DB queries — no in-memory processing.
     *
     * @return 200 with {@link DashboardSummaryResponse} wrapped in {@link ApiResponse}
     */
    @GetMapping("/api/v1/admin/dashboard/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary     = "Admin: dashboard KPI summary",
            description = "Returns aggregated platform metrics including revenue, order counts by " +
                          "status, low-stock alerts, pending returns, recent orders, and 7-day " +
                          "revenue trend. All values are computed via optimized DB aggregate queries."
    )
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary() {
        DashboardSummaryResponse summary = dashboardService.getSummary();
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary retrieved.", summary));
    }
}
