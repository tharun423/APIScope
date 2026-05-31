package com.apiscope.sample.controller;

import com.apiscope.sample.repository.dao.CustomerRecordDao;
import com.apiscope.sample.repository.dao.PaymentRecordDao;
import com.apiscope.sample.repository.dao.ProductCatalogDao;
import com.apiscope.sample.repository.dao.SalesOrderDao;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reporting &amp; Analytics API — dashboards, KPIs, exports, and scheduled reports.
 * Key endpoints are backed by real H2 database queries via JPA DAOs.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final SalesOrderDao     salesOrderDao;
    private final CustomerRecordDao customerRecordDao;
    private final ProductCatalogDao productCatalogDao;
    private final PaymentRecordDao  paymentRecordDao;

    public AnalyticsController(SalesOrderDao salesOrderDao, CustomerRecordDao customerRecordDao,
                               ProductCatalogDao productCatalogDao, PaymentRecordDao paymentRecordDao) {
        this.salesOrderDao     = salesOrderDao;
        this.customerRecordDao = customerRecordDao;
        this.productCatalogDao = productCatalogDao;
        this.paymentRecordDao  = paymentRecordDao;
    }

    /** Real revenue query: SUM(total_amount) between date range, with optional day-series breakdown. */
    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenue(
            @RequestParam String fromDate,
            @RequestParam String toDate,
            @RequestParam(defaultValue = "DAY") String groupBy,
            @RequestParam(defaultValue = "USD") String currency) {

        LocalDate from = parseDate(fromDate, LocalDate.now().minusDays(30));
        LocalDate to   = parseDate(toDate,   LocalDate.now());
        double totalRevenue = salesOrderDao.sumRevenueBetween(from, to);

        List<Map<String, Object>> series = new ArrayList<>();
        if ("DAY".equalsIgnoreCase(groupBy)) {
            for (Object[] row : salesOrderDao.dailyRevenueSeries(from, to)) {
                Map<String, Object> point = new HashMap<>();
                point.put("date",       row[0] != null ? row[0].toString() : null);
                point.put("revenue",    row[1]);
                point.put("orderCount", row[2]);
                series.add(point);
            }
        }

        return ResponseEntity.ok(Map.of(
                "totalRevenue", totalRevenue,
                "currency",     currency,
                "groupBy",      groupBy,
                "from",         from.toString(),
                "to",           to.toString(),
                "series",       series
        ));
    }

    /** Real top-sellers query: products ranked by total units sold. */
    @GetMapping("/products/top-selling")
    public ResponseEntity<Map<String, Object>> getTopSellingProducts(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String category) {

        List<Map<String, Object>> products = new ArrayList<>();
        for (Object[] row : productCatalogDao.findTopSellingProducts(limit)) {
            Map<String, Object> p = new HashMap<>();
            p.put("sku",       row[0]);
            p.put("name",      row[1]);
            p.put("category",  row[2]);
            p.put("totalSold", row[3]);
            if (category == null || category.equalsIgnoreCase((String) row[2])) {
                products.add(p);
            }
        }

        LocalDate from = parseDate(fromDate, LocalDate.now().minusDays(90));
        LocalDate to   = parseDate(toDate,   LocalDate.now());
        return ResponseEntity.ok(Map.of("products", products, "period",
                Map.of("from", from.toString(), "to", to.toString())));
    }

    /** Real customer metrics: new registrations, active customers, churn proxy. */
    @GetMapping("/customers/metrics")
    public ResponseEntity<Map<String, Object>> getCustomerMetrics(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String segment) {

        LocalDate from = parseDate(fromDate, LocalDate.now().minusDays(30));
        LocalDate to   = parseDate(toDate,   LocalDate.now());
        long newCustomers    = customerRecordDao.countByCreatedDateBetween(from, to);
        long totalCustomers  = customerRecordDao.count();
        long activeCustomers = customerRecordDao.countActiveCustomers();
        long churned         = totalCustomers - activeCustomers;

        return ResponseEntity.ok(Map.of(
                "newCustomers",    newCustomers,
                "totalCustomers",  totalCustomers,
                "activeCustomers", activeCustomers,
                "churned",         churned,
                "period",          Map.of("from", from.toString(), "to", to.toString())
        ));
    }

    /** Real fulfilment metrics derived from order status counts. */
    @GetMapping("/fulfilment/metrics")
    public ResponseEntity<Map<String, Object>> getFulfilmentMetrics(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String warehouseId) {

        List<Object[]> statusCounts = salesOrderDao.countByStatusGroup();
        long total      = 0;
        long delivered  = 0;
        for (Object[] row : statusCounts) {
            long cnt = ((Number) row[1]).longValue();
            total += cnt;
            if ("DELIVERED".equals(row[0])) delivered = cnt;
        }
        double onTimeRate = total > 0 ? Math.round(((double) delivered / total) * 1000.0) / 10.0 : 0.0;

        return ResponseEntity.ok(Map.of(
                "ordersProcessed",       total,
                "deliveredOrders",       delivered,
                "onTimeDeliveryRate",    onTimeRate + "%",
                "statusBreakdown",       buildStatusMap(statusCounts)
        ));
    }

    /** Real KPI dashboard: live counts from the database. */
    @GetMapping("/dashboard/kpis")
    public ResponseEntity<Map<String, Object>> getDashboardKpis() {
        long   ordersToday   = salesOrderDao.countOrdersToday(LocalDate.now());
        double revenueToday  = salesOrderDao.sumRevenueBetween(LocalDate.now(), LocalDate.now());
        long   totalProducts = productCatalogDao.count();
        long   lowStock      = productCatalogDao.findByStockQuantityLessThan(20).size();
        long   pendingRefunds = paymentRecordDao.countPendingRefunds();
        long   totalCustomers = customerRecordDao.count();

        return ResponseEntity.ok(Map.of(
                "ordersToday",    ordersToday,
                "revenueToday",   revenueToday,
                "totalProducts",  totalProducts,
                "totalCustomers", totalCustomers,
                "lowStockAlerts", lowStock,
                "pendingRefunds", pendingRefunds,
                "asOf",           java.time.Instant.now().toString()
        ));
    }

    @PostMapping("/reports/export")
    public ResponseEntity<Map<String, Object>> exportReport(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(Map.of(
                "exportId",                UUID.randomUUID().toString(),
                "status",                  "PROCESSING",
                "format",                  request.getOrDefault("format", "CSV"),
                "downloadUrlAvailableIn",  "30 seconds"
        ));
    }

    @GetMapping("/reports/export/{exportId}/download")
    public ResponseEntity<Map<String, Object>> downloadExport(@PathVariable String exportId) {
        return ResponseEntity.ok(Map.of(
                "exportId",    exportId,
                "downloadUrl", "https://exports.example.com/" + exportId + ".csv",
                "expiresAt",   "2026-05-12T16:00:00Z"
        ));
    }

    @PostMapping("/reports/schedule")
    public ResponseEntity<Map<String, Object>> scheduleReport(@RequestBody Map<String, Object> request) {
        return ResponseEntity.status(201).body(Map.of(
                "scheduleId", UUID.randomUUID().toString(),
                "reportType", request.getOrDefault("reportType", ""),
                "schedule", request.getOrDefault("schedule", "WEEKLY"),
                "nextRun", "2026-05-06T06:00:00Z"
        ));
    }

    @GetMapping("/customers/cohort-retention")
    public ResponseEntity<Map<String, Object>> getCohortRetention(
            @RequestParam(defaultValue = "2026-01") String cohortMonth,
            @RequestParam(defaultValue = "6") int periods) {
        return ResponseEntity.ok(Map.of(
                "cohortMonth", cohortMonth,
                "retentionByPeriod", List.of()
        ));
    }

    @GetMapping("/payments/failure-analysis")
    public ResponseEntity<Map<String, Object>> getPaymentFailureAnalysis(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        long refunded = paymentRecordDao.countPendingRefunds();
        return ResponseEntity.ok(Map.of(
                "totalRefunded", refunded,
                "topReasons", List.of(
                        Map.of("reason", "INSUFFICIENT_FUNDS", "count", 145),
                        Map.of("reason", "CARD_EXPIRED", "count", 88)
                )
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return LocalDate.parse(value); } catch (DateTimeParseException e) { return fallback; }
    }

    private Map<String, Object> buildStatusMap(List<Object[]> rows) {
        Map<String, Object> m = new HashMap<>();
        for (Object[] row : rows) m.put((String) row[0], row[1]);
        return m;
    }
}
