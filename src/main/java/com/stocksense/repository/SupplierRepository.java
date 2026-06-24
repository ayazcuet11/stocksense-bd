package com.stocksense.repository;

import com.stocksense.domain.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    List<Supplier> findAllByTenantId(Long tenantId);
    Optional<Supplier> findByIdAndTenantId(Long id, Long tenantId);
}
