package com.stocksense.service;

import com.stocksense.domain.Sale;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.BranchRepository;
import com.stocksense.repository.SaleRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final BranchRepository branchRepository;

    public SaleService(SaleRepository saleRepository, BranchRepository branchRepository) {
        this.saleRepository = saleRepository;
        this.branchRepository = branchRepository;
    }

    public List<Sale> listByBranch(Long branchId) {
        assertBranchBelongsToTenant(branchId);
        return saleRepository.findAllByBranchId(branchId);
    }

    public Sale getById(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found: " + id));
        assertBranchBelongsToTenant(sale.getBranchId());
        return sale;
    }

    @Transactional
    public Sale record(Sale sale) {
        assertBranchBelongsToTenant(sale.getBranchId());
        sale.getLines().forEach(line -> line.setSale(sale));
        return saleRepository.save(sale);
    }

    private void assertBranchBelongsToTenant(Long branchId) {
        if (!branchRepository.existsByIdAndTenantId(branchId, TenantContext.get())) {
            throw new ResourceNotFoundException("Branch not found: " + branchId);
        }
    }
}
