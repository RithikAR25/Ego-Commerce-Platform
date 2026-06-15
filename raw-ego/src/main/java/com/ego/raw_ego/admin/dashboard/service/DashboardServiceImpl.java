package com.ego.raw_ego.admin.dashboard.service;

import com.ego.raw_ego.admin.dashboard.dto.DashboardSummaryResponse;
import com.ego.raw_ego.admin.dashboard.dto.RecentOrderRow;
import com.ego.raw_ego.admin.dashboard.repository.DashboardRepository;
import com.ego.raw_ego.catalog.repository.InventoryRecordRepository;
import com.ego.raw_ego.order.enums.OrderStatus;
import com.ego.raw_ego.returns.enums.ReturnStatus;
import com.ego.raw_ego.returns.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link DashboardService} — assembles the admin dashboard KPI summary
 * from targeted aggregate queries.
 *
 * <h3>Query strategy</h3>
 * Each metric is fetched with a single aggregate SQL statement — no entity collections
 * are loaded into memory. The 7-day revenue trend requires 7 lightweight date-filtered
 * SUM queries (one per day), which is acceptable at any realistic order volume.
 *
 * <h3>Cross-domain calls</h3>
 * The dashboard deliberately aggregates across three domain repositories:
 * <ul>
 *   <li>{@link DashboardRepository}          — Order aggregates</li>
 *   <li>{@link InventoryRecordRepository}    — Low-stock count</li>
 *   <li>{@link ReturnRequestRepository}      — Pending return count</li>
 * </ul>
 * This is intentional: a dashboard is inherently cross-domain.
 * None of these calls mutate state — the entire method is {@code readOnly = true}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    // ── Revenue statuses — PENDING_PAYMENT and CANCELLED excluded (no confirmed revenue)
    private static final List<OrderStatus> REVENUE_STATUSES = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED,
            OrderStatus.REFUNDED
    );

    // ── Active backlog — orders not yet fulfilled or closed
    private static final List<OrderStatus> PENDING_STATUSES = List.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final DashboardRepository         dashboardRepository;
    private final InventoryRecordRepository   inventoryRecordRepository;
    private final ReturnRequestRepository     returnRequestRepository;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {

        // ── 1. Total revenue (confirmed+ only) ──────────────────────────────────
        BigDecimal rawRevenue = dashboardRepository.sumGrandTotalByStatusIn(REVENUE_STATUSES);
        BigDecimal totalRevenue = rawRevenue != null ? rawRevenue : BigDecimal.ZERO;

        // ── 2. Order counts ──────────────────────────────────────────────────────
        long totalOrders     = dashboardRepository.countAllOrders();
        long pendingOrders   = dashboardRepository.countByStatusIn(PENDING_STATUSES);
        long deliveredOrders = dashboardRepository.countByStatusIn(List.of(OrderStatus.DELIVERED));

        // ── 3. Cross-domain counts ───────────────────────────────────────────────
        long refundCount   = returnRequestRepository.countByStatus(ReturnStatus.REQUESTED);
        long lowStockCount = inventoryRecordRepository.countLowStockItems();

        // ── 4. Orders grouped by status (for doughnut chart) ────────────────────
        List<Object[]> groupedRows = dashboardRepository.countGroupedByStatus();
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        for (Object[] row : groupedRows) {
            String statusName = ((OrderStatus) row[0]).name();
            Long   count      = (Long) row[1];
            ordersByStatus.put(statusName, count);
        }

        // ── 5. Last 10 orders (lightweight projection) ───────────────────────────
        List<RecentOrderRow> recentOrders = dashboardRepository
                .findRecentOrders(PageRequest.of(0, 10))
                .getContent();

        // ── 6. Revenue trend — 7 calendar days (oldest → newest) ────────────────
        List<BigDecimal> revenueTrend = new ArrayList<>(7);
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            String     day        = today.minusDays(i).format(DATE_FMT);
            BigDecimal dayRevenue = dashboardRepository.revenueForDay(day);
            revenueTrend.add(dayRevenue != null ? dayRevenue : BigDecimal.ZERO);
        }

        log.debug("Dashboard summary computed: totalOrders={} totalRevenue={} lowStock={} pendingReturns={}",
                totalOrders, totalRevenue, lowStockCount, refundCount);

        return DashboardSummaryResponse.builder()
                .totalRevenue(totalRevenue)
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .deliveredOrders(deliveredOrders)
                .refundCount(refundCount)
                .lowStockCount(lowStockCount)
                .recentOrders(recentOrders)
                .ordersByStatus(ordersByStatus)
                .revenueTrend(revenueTrend)
                .build();
    }
}
