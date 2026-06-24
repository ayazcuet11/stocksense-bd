package com.stocksense.repository;

import com.stocksense.domain.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findAllByBranchId(Long branchId);
    List<Sale> findAllByBranchIdAndSoldAtBetween(Long branchId, Instant from, Instant to);
    List<Sale> findAllByBranchIdIn(List<Long> branchIds);

    @Query("SELECT MIN(s.soldAt) FROM Sale s WHERE s.branchId = :branchId")
    Instant findEarliestSoldAt(Long branchId);

    @Query("SELECT MAX(s.soldAt) FROM Sale s WHERE s.branchId = :branchId")
    Instant findLatestSoldAt(Long branchId);
}
