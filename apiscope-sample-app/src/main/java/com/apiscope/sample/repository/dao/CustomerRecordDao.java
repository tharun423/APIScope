package com.apiscope.sample.repository.dao;

import com.apiscope.sample.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Customer}.
 * Used by analytics and user management endpoints.
 */
public interface CustomerRecordDao extends JpaRepository<Customer, Long> {

    /** Find a customer by their unique email address. */
    Optional<Customer> findByEmail(String email);

    /** Count new customers registered strictly after the given date. */
    long countByCreatedDateAfter(LocalDate date);

    /** Count new customers registered within an inclusive date range. */
    long countByCreatedDateBetween(LocalDate from, LocalDate to);

    /**
     * Count customers who placed at least one order (churned = never placed).
     * Used in customer metrics to compute active vs churned rates.
     */
    @Query("SELECT COUNT(DISTINCT o.customerId) FROM SalesOrder o WHERE o.status != 'CANCELLED'")
    long countActiveCustomers();

    /**
     * Count customers registered in a specific country.
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.country = :country")
    long countByCountry(@Param("country") String country);
}
