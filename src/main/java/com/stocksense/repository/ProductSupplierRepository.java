package com.stocksense.repository;

import com.stocksense.domain.ProductSupplier;
import com.stocksense.domain.ProductSupplierId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductSupplierRepository extends JpaRepository<ProductSupplier, ProductSupplierId> {
    List<ProductSupplier> findAllById_ProductId(Long productId);
    List<ProductSupplier> findAllById_SupplierId(Long supplierId);
}
