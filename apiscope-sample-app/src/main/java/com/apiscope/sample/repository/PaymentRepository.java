package com.apiscope.sample.repository;

import com.apiscope.sample.entity.Payment;
import com.apiscope.sample.repository.dao.PaymentRecordDao;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AOP-visible REPOSITORY layer for payment operations.
 * {@link #createPaymentRecord} persists real {@link Payment} rows to the H2 database;
 * {@link #validatePaymentMethod} simulates the external payment-gateway call (no DB).
 *
 * <p>This class uses {@code @Repository} so that {@code FlowAspect} intercepts its
 * methods and shows a REPOSITORY step in the Flow Tracer execution diagram.
 */
@Repository
public class PaymentRepository {

    private final PaymentRecordDao paymentRecordDao;

    public PaymentRepository(PaymentRecordDao paymentRecordDao) {
        this.paymentRecordDao = paymentRecordDao;
    }

    /**
     * Validate a stored payment method via the (simulated) payment gateway.
     * No DB write — gateway calls are stateless.
     *
     * @param paymentMethodId identifier of the stored payment method
     * @return validation result with masked card details
     */
    public Map<String, Object> validatePaymentMethod(String paymentMethodId) {
        simulateLatency(25);
        return Map.of(
                "paymentMethodId", paymentMethodId,
                "valid",           true,
                "type",            "CREDIT_CARD",
                "maskedNumber",    "**** **** **** 4242",
                "expiryMonth",     12,
                "expiryYear",      2028
        );
    }

    /**
     * Persist a new payment record in PENDING state.
     * Executes: {@code INSERT INTO payment (...) VALUES (...)}
     *
     * @param orderId the order this payment belongs to
     * @param amount  the amount to charge in USD
     * @return created payment record
     */
    public Map<String, Object> createPaymentRecord(String orderId, double amount) {
        String paymentRef = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = new Payment();
        payment.setPaymentRef(paymentRef);
        payment.setOrderRef(orderId);
        payment.setAmount(amount);
        payment.setCurrency("USD");
        payment.setStatus("PENDING");
        payment.setPaymentMethodId("pm_flow_tracer");
        payment.setCreatedAt(LocalDateTime.now());
        paymentRecordDao.save(payment);

        return Map.of(
                "paymentId",  paymentRef,
                "orderId",    orderId,
                "amount",     amount,
                "currency",   "USD",
                "status",     "PENDING",
                "createdAt",  payment.getCreatedAt().toString()
        );
    }

    private void simulateLatency(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
