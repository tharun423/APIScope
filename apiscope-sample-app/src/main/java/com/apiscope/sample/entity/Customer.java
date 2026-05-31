package com.apiscope.sample.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * JPA entity representing a registered customer.
 * Mapped to the {@code customer} table, seeded via {@code data.sql}.
 */
@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    private String phone;
    private String city;
    private String country;
    private LocalDate createdDate;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId()                       { return id; }
    public void setId(Long id)               { this.id = id; }

    public String getEmail()                 { return email; }
    public void setEmail(String email)       { this.email = email; }

    public String getName()                  { return name; }
    public void setName(String name)         { this.name = name; }

    public String getPhone()                 { return phone; }
    public void setPhone(String phone)       { this.phone = phone; }

    public String getCity()                  { return city; }
    public void setCity(String city)         { this.city = city; }

    public String getCountry()               { return country; }
    public void setCountry(String country)   { this.country = country; }

    public LocalDate getCreatedDate()                  { return createdDate; }
    public void setCreatedDate(LocalDate createdDate)  { this.createdDate = createdDate; }
}
