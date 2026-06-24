package com.stocksense.repository;

import com.stocksense.domain.OrderStatus;
import com.stocksense.domain.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findAllByTenantId(Long tenantId);
    List<PurchaseOrder> findAllByTenantIdAndStatus(Long tenantId, OrderStatus status);
    Optional<PurchaseOrder> findByIdAndTenantId(Long id, Long tenantId);
}
