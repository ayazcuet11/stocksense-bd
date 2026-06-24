package com.stocksense.repository;

import com.stocksense.domain.SaleLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface SaleLineRepository extends JpaRepository<SaleLine, Long> {

    @Query("""
            SELECT sl FROM SaleLine sl JOIN sl.sale s
            WHERE s.branchId = :branchId AND sl.productId = :productId
            AND s.soldAt BETWEEN :from AND :to
            """)
    List<SaleLine> findByBranchProductAndDateRange(Long branchId, Long productId, Instant from, Instant to);
}
