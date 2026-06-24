package com.stocksense.repository;

import com.stocksense.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByTenantId(Long tenantId);
    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);
    List<Product> findAllByCategoryIdAndTenantId(Long categoryId, Long tenantId);
}
