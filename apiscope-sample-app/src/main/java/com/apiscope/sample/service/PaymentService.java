package com.apiscope.sample.service;

import com.apiscope.sample.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Payment service — validates payment methods and initiates charges.
 * Shown as SERVICE layer in the Flow Tracer execution diagram.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Validate the customer's stored payment method before charging.
     *
     * @param paymentMethodId the stored payment method identifier
     * @return validation result
     * @throws IllegalArgumentException if the payment method is invalid or expired
     */
    public Map<String, Object> validatePayment(String paymentMethodId) {
        Map<String, Object> result = paymentRepository.validatePaymentMethod(paymentMethodId);
        boolean valid = (boolean) result.get("valid");

        if (!valid) {
            throw new IllegalArgumentException(
                "Payment method " + paymentMethodId + " is invalid or expired");
        }

        return result;
    }

    /**
     * Authorise and record a payment for the given order.
     *
     * @param orderId         the order to charge
     * @param amount          amount in USD
     * @param paymentMethodId the validated payment method
     * @return payment record in AUTHORISED state
     */
    public Map<String, Object> authorisePayment(String orderId, double amount, String paymentMethodId) {
        // Simulate the payment gateway call latency
        try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        Map<String, Object> record = paymentRepository.createPaymentRecord(orderId, amount);

        return Map.of(
                "paymentId",        record.get("paymentId"),
                "orderId",          orderId,
                "amount",           amount,
                "paymentMethodId",  paymentMethodId,
                "status",           "AUTHORISED",
                "authCode",         "AUTH-" + (int)(Math.random() * 900000 + 100000)
        );
    }
}
