package com.stocksense.service;

import com.stocksense.domain.BranchStock;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.BranchRepository;
import com.stocksense.repository.BranchStockRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BranchStockService {

    private final BranchStockRepository branchStockRepository;
    private final BranchRepository branchRepository;

    public BranchStockService(BranchStockRepository branchStockRepository,
                               BranchRepository branchRepository) {
        this.branchStockRepository = branchStockRepository;
        this.branchRepository = branchRepository;
    }

    public List<BranchStock> listByBranch(Long branchId) {
        assertBranchBelongsToTenant(branchId);
        return branchStockRepository.findAllByBranchId(branchId);
    }

    public List<BranchStock> listLowStock(Long branchId) {
        assertBranchBelongsToTenant(branchId);
        return branchStockRepository.findLowStockByBranchId(branchId);
    }

    public BranchStock getByBranchAndProduct(Long branchId, Long productId) {
        assertBranchBelongsToTenant(branchId);
        return branchStockRepository.findByBranchIdAndProductId(branchId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Stock not found for branch " + branchId + " product " + productId));
    }

    @Transactional
    public BranchStock upsert(Long branchId, Long productId, int quantity, int reorderThreshold) {
        assertBranchBelongsToTenant(branchId);
        BranchStock stock = branchStockRepository
                .findByBranchIdAndProductId(branchId, productId)
                .orElseGet(() -> {
                    BranchStock s = new BranchStock();
                    s.setBranchId(branchId);
                    s.setProductId(productId);
                    return s;
                });
        stock.setQuantity(quantity);
        stock.setReorderThreshold(reorderThreshold);
        return branchStockRepository.save(stock);
    }

    @Transactional
    public BranchStock adjustQuantity(Long branchId, Long productId, int delta) {
        BranchStock stock = getByBranchAndProduct(branchId, productId);
        int newQty = stock.getQuantity() + delta;
        if (newQty < 0) throw new IllegalArgumentException("Stock cannot go below zero");
        stock.setQuantity(newQty);
        return branchStockRepository.save(stock);
    }

    private void assertBranchBelongsToTenant(Long branchId) {
        if (!branchRepository.existsByIdAndTenantId(branchId, TenantContext.get())) {
            throw new ResourceNotFoundException("Branch not found: " + branchId);
        }
    }
}
