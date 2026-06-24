package com.stocksense.repository;

import com.stocksense.domain.BranchStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BranchStockRepository extends JpaRepository<BranchStock, Long> {

    List<BranchStock> findAllByBranchId(Long branchId);
    Optional<BranchStock> findByBranchIdAndProductId(Long branchId, Long productId);

    @Query("SELECT bs FROM BranchStock bs WHERE bs.branchId = :branchId AND bs.quantity <= bs.reorderThreshold")
    List<BranchStock> findLowStockByBranchId(Long branchId);
}
