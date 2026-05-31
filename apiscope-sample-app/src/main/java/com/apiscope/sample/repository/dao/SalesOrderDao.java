package com.apiscope.sample.repository.dao;

import com.apiscope.sample.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SalesOrder}.
 *
 * <p>Custom queries power the analytics and order management endpoints.
 */
public interface SalesOrderDao extends JpaRepository<SalesOrder, Long> {

    /** All orders placed by a specific customer. */
    List<SalesOrder> findByCustomerId(Long customerId);

    /** Orders filtered by lifecycle status (CONFIRMED / SHIPPED / DELIVERED / CANCELLED). */
    List<SalesOrder> findByStatus(String status);

    /** Look up a single order by its human-readable reference (e.g. "ORD-00000001"). */
    Optional<SalesOrder> findByOrderRef(String orderRef);

    /**
     * Total revenue between two dates (inclusive), excluding cancelled orders.
     * Returns {@code 0.0} when no orders match (COALESCE prevents null).
     */
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0.0) FROM SalesOrder o " +
           "WHERE o.createdDate BETWEEN :from AND :to AND o.status != 'CANCELLED'")
    Double sumRevenueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Count of orders placed today (for the KPI dashboard).
     */
    @Query("SELECT COUNT(o) FROM SalesOrder o WHERE o.createdDate = :today")
    long countOrdersToday(@Param("today") LocalDate today);

    /**
     * Order counts grouped by status — used for fulfilment metrics.
     * Returns rows of [status (String), count (Long)].
     */
    @Query("SELECT o.status, COUNT(o) FROM SalesOrder o GROUP BY o.status")
    List<Object[]> countByStatusGroup();

    /**
     * Native query: day-by-day revenue series for the chart endpoint.
     * Returns rows of [day (Date), revenue (Double), order_count (Long)].
     */
    @Query(value = """
            SELECT CAST(o.created_date AS DATE)   AS day,
                   SUM(o.total_amount)             AS revenue,
                   COUNT(o.id)                     AS order_count
            FROM   sales_order o
            WHERE  o.created_date BETWEEN :from AND :to
              AND  o.status != 'CANCELLED'
            GROUP  BY CAST(o.created_date AS DATE)
            ORDER  BY day
            """, nativeQuery = true)
    List<Object[]> dailyRevenueSeries(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
