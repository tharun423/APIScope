package com.apiscope.sample.entity;

import jakarta.persistence.*;

/**
 * JPA entity representing a product in the catalogue.
 * Mapped to the {@code product} table which is auto-seeded via {@code data.sql}.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String category;
    private Double price;
    private Integer stockQuantity;
    private String warehouseId;
    private Integer reorderLevel;

    /** ACTIVE | DRAFT | DEACTIVATED */
    private String status;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId()                       { return id; }
    public void setId(Long id)               { this.id = id; }

    public String getSku()                   { return sku; }
    public void setSku(String sku)           { this.sku = sku; }

    public String getName()                  { return name; }
    public void setName(String name)         { this.name = name; }

    public String getCategory()              { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getPrice()                 { return price; }
    public void setPrice(Double price)       { this.price = price; }

    public Integer getStockQuantity()                   { return stockQuantity; }
    public void setStockQuantity(Integer stockQuantity) { this.stockQuantity = stockQuantity; }

    public String getWarehouseId()                   { return warehouseId; }
    public void setWarehouseId(String warehouseId)   { this.warehouseId = warehouseId; }

    public Integer getReorderLevel()                    { return reorderLevel; }
    public void setReorderLevel(Integer reorderLevel)   { this.reorderLevel = reorderLevel; }

    public String getStatus()                { return status; }
    public void setStatus(String status)     { this.status = status; }
}
