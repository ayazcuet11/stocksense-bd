package com.stocksense.service;

import com.stocksense.domain.ProductSupplier;
import com.stocksense.domain.Supplier;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.ProductSupplierRepository;
import com.stocksense.repository.SupplierRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductSupplierRepository productSupplierRepository;

    public SupplierService(SupplierRepository supplierRepository,
                           ProductSupplierRepository productSupplierRepository) {
        this.supplierRepository = supplierRepository;
        this.productSupplierRepository = productSupplierRepository;
    }

    public List<Supplier> listAll() {
        return supplierRepository.findAllByTenantId(TenantContext.get());
    }

    public Supplier getById(Long id) {
        return supplierRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + id));
    }

    @Transactional
    public Supplier create(Supplier supplier) {
        supplier.setTenantId(TenantContext.get());
        return supplierRepository.save(supplier);
    }

    @Transactional
    public Supplier update(Long id, Supplier patch) {
        Supplier existing = getById(id);
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getPhone() != null) existing.setPhone(patch.getPhone());
        if (patch.getLeadTimeDays() != null) existing.setLeadTimeDays(patch.getLeadTimeDays());
        return supplierRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        supplierRepository.delete(getById(id));
    }

    public List<ProductSupplier> getPricesForProduct(Long productId) {
        return productSupplierRepository.findAllById_ProductId(productId);
    }

    @Transactional
    public ProductSupplier saveProductPrice(ProductSupplier ps) {
        return productSupplierRepository.save(ps);
    }
}
