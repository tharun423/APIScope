package com.apiscope.sample.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity representing a payment record.
 * Mapped to the {@code payment} table, partially seeded via {@code data.sql}
 * and populated at runtime by {@link com.apiscope.sample.repository.PaymentRepository}.
 */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference, e.g. "PAY-00000001". */
    @Column(nullable = false, unique = true)
    private String paymentRef;

    /** References {@link SalesOrder#orderRef}. */
    @Column(nullable = false)
    private String orderRef;

    private Double amount;
    private String currency;

    /** PENDING | AUTHORISED | CAPTURED | REFUNDED | FAILED */
    private String status;

    private String paymentMethodId;
    private String authCode;
    private LocalDateTime createdAt;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId()                             { return id; }
    public void setId(Long id)                     { this.id = id; }

    public String getPaymentRef()                   { return paymentRef; }
    public void setPaymentRef(String paymentRef)    { this.paymentRef = paymentRef; }

    public String getOrderRef()                     { return orderRef; }
    public void setOrderRef(String orderRef)        { this.orderRef = orderRef; }

    public Double getAmount()                       { return amount; }
    public void setAmount(Double amount)            { this.amount = amount; }

    public String getCurrency()                     { return currency; }
    public void setCurrency(String currency)        { this.currency = currency; }

    public String getStatus()                       { return status; }
    public void setStatus(String status)            { this.status = status; }

    public String getPaymentMethodId()                          { return paymentMethodId; }
    public void setPaymentMethodId(String paymentMethodId)      { this.paymentMethodId = paymentMethodId; }

    public String getAuthCode()                     { return authCode; }
    public void setAuthCode(String authCode)        { this.authCode = authCode; }

    public LocalDateTime getCreatedAt()                     { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)       { this.createdAt = createdAt; }
}
