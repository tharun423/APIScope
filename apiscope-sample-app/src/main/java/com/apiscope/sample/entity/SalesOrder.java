package com.apiscope.sample.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * JPA entity representing a customer's placed order.
 * Named {@code SalesOrder} to avoid clashing with the SQL reserved word {@code ORDER}.
 * Mapped to the {@code sales_order} table, seeded via {@code data.sql}.
 */
@Entity
@Table(name = "sales_order")
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable reference, e.g. "ORD-00000001". */
    @Column(nullable = false, unique = true)
    private String orderRef;

    private Long customerId;
    private Long productId;
    private Integer quantity;
    private Double unitPrice;
    private Double totalAmount;

    /** CONFIRMED | SHIPPED | DELIVERED | CANCELLED */
    private String status;

    private LocalDate createdDate;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                  { this.id = id; }

    public String getOrderRef()                  { return orderRef; }
    public void setOrderRef(String orderRef)     { this.orderRef = orderRef; }

    public Long getCustomerId()                  { return customerId; }
    public void setCustomerId(Long customerId)   { this.customerId = customerId; }

    public Long getProductId()                   { return productId; }
    public void setProductId(Long productId)     { this.productId = productId; }

    public Integer getQuantity()                 { return quantity; }
    public void setQuantity(Integer quantity)    { this.quantity = quantity; }

    public Double getUnitPrice()                 { return unitPrice; }
    public void setUnitPrice(Double unitPrice)   { this.unitPrice = unitPrice; }

    public Double getTotalAmount()               { return totalAmount; }
    public void setTotalAmount(Double total)     { this.totalAmount = total; }

    public String getStatus()                    { return status; }
    public void setStatus(String status)         { this.status = status; }

    public LocalDate getCreatedDate()                   { return createdDate; }
    public void setCreatedDate(LocalDate createdDate)   { this.createdDate = createdDate; }
}
