package com.apiscope.sample.repository.dao;

import com.apiscope.sample.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Payment}.
 * Used by {@link com.apiscope.sample.repository.PaymentRepository} (the AOP-visible
 * REPOSITORY layer) and the payments controller.
 */
public interface PaymentRecordDao extends JpaRepository<Payment, Long> {

    /** All payments associated with a given order reference. */
    List<Payment> findByOrderRef(String orderRef);

    /** Payments filtered by status (PENDING / AUTHORISED / CAPTURED / REFUNDED / FAILED). */
    List<Payment> findByStatus(String status);

    /** Look up a payment by its human-readable reference (e.g. "PAY-00000001"). */
    Optional<Payment> findByPaymentRef(String paymentRef);

    /**
     * Count pending refunds (payments in REFUNDED status that require processing).
     */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'REFUNDED'")
    long countPendingRefunds();

    /**
     * Total captured revenue across all payments in CAPTURED status.
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0.0) FROM Payment p WHERE p.status = 'CAPTURED'")
    Double sumCapturedRevenue();
}
