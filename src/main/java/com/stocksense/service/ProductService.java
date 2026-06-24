package com.stocksense.service;

import com.stocksense.domain.Product;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.ProductRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> listAll() {
        return productRepository.findAllByTenantId(TenantContext.get());
    }

    public List<Product> listByCategory(Long categoryId) {
        return productRepository.findAllByCategoryIdAndTenantId(categoryId, TenantContext.get());
    }

    public Product getById(Long id) {
        return productRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    @Transactional
    public Product create(Product product) {
        product.setTenantId(TenantContext.get());
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, Product patch) {
        Product existing = getById(id);
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getSku() != null) existing.setSku(patch.getSku());
        if (patch.getUnit() != null) existing.setUnit(patch.getUnit());
        if (patch.getCategoryId() != null) existing.setCategoryId(patch.getCategoryId());
        if (patch.getVatRate() != null) existing.setVatRate(patch.getVatRate());
        return productRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        productRepository.delete(getById(id));
    }
}
