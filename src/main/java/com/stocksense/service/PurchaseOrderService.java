package com.stocksense.service;

import com.stocksense.domain.OrderStatus;
import com.stocksense.domain.Product;
import com.stocksense.domain.PurchaseOrder;
import com.stocksense.domain.PurchaseOrderLine;
import com.stocksense.domain.Supplier;
import com.stocksense.dto.reorder.DraftLine;
import com.stocksense.dto.reorder.PurchaseOrderView;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.BranchRepository;
import com.stocksense.repository.ProductRepository;
import com.stocksense.repository.PurchaseOrderRepository;
import com.stocksense.repository.SupplierRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final BranchRepository branchRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public PurchaseOrderService(PurchaseOrderRepository poRepository,
                                BranchRepository branchRepository,
                                SupplierRepository supplierRepository,
                                ProductRepository productRepository) {
        this.poRepository = poRepository;
        this.branchRepository = branchRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    public List<PurchaseOrder> listAll() {
        return poRepository.findAllByTenantId(TenantContext.get());
    }

    public List<PurchaseOrder> listPendingApproval() {
        return poRepository.findAllByTenantIdAndStatus(TenantContext.get(), OrderStatus.PENDING_APPROVAL);
    }

    /** POs enriched with supplier/product names and line totals (lines loaded eagerly within the tx). */
    @Transactional(readOnly = true)
    public List<PurchaseOrderView> listViews(OrderStatus statusFilter) {
        Long tenant = TenantContext.get();
        List<PurchaseOrder> pos = statusFilter == null
                ? poRepository.findAllByTenantId(tenant)
                : poRepository.findAllByTenantIdAndStatus(tenant, statusFilter);

        Map<Long, Product> products = productRepository.findAllByTenantId(tenant).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, Supplier> suppliers = supplierRepository.findAllByTenantId(tenant).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));

        return pos.stream()
                .map(po -> toView(po, products, suppliers))
                .toList();
    }

    private PurchaseOrderView toView(PurchaseOrder po, Map<Long, Product> products, Map<Long, Supplier> suppliers) {
        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrderView.LineView> lines = new java.util.ArrayList<>();
        for (PurchaseOrderLine line : po.getLines()) {
            BigDecimal lineTotal = line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            total = total.add(lineTotal);
            Product p = products.get(line.getProductId());
            lines.add(new PurchaseOrderView.LineView(
                    line.getProductId(),
                    p == null ? null : p.getSku(),
                    p == null ? null : p.getName(),
                    line.getQuantity(),
                    line.getUnitPrice(),
                    lineTotal));
        }
        Supplier s = suppliers.get(po.getSupplierId());
        return new PurchaseOrderView(
                po.getId(), po.getBranchId(), po.getSupplierId(),
                s == null ? null : s.getName(),
                po.getStatus(), Boolean.TRUE.equals(po.getCreatedByAgent()),
                po.getApprovedBy(), po.getCreatedAt(), total, lines);
    }

    public PurchaseOrder getById(Long id) {
        return poRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Purchase order not found: " + id));
    }

    @Transactional
    public PurchaseOrder create(PurchaseOrder po) {
        po.setTenantId(TenantContext.get());
        po.setStatus(OrderStatus.DRAFT);
        po.getLines().forEach(line -> line.setPurchaseOrder(po));
        return poRepository.save(po);
    }

    /**
     * Creates an agent-drafted PO straight into PENDING_APPROVAL. This is the human-in-the-loop gate:
     * the agent may draft, but only a manager's {@link #approve} call advances it. Tenant-scoped and
     * ownership-validated — agent tool inputs are untrusted.
     */
    @Transactional
    public PurchaseOrder createAgentDraft(Long branchId, Long supplierId, List<DraftLine> draftLines) {
        Long tenant = TenantContext.get();
        if (!branchRepository.existsByIdAndTenantId(branchId, tenant)) {
            throw new ResourceNotFoundException("Branch not found: " + branchId);
        }
        supplierRepository.findByIdAndTenantId(supplierId, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found: " + supplierId));
        if (draftLines == null || draftLines.isEmpty()) {
            throw new IllegalArgumentException("A purchase order needs at least one line");
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setTenantId(tenant);
        po.setBranchId(branchId);
        po.setSupplierId(supplierId);
        po.setCreatedByAgent(true);
        po.setStatus(OrderStatus.PENDING_APPROVAL);
        for (DraftLine dl : draftLines) {
            Product product = productRepository.findByIdAndTenantId(dl.productId(), tenant)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + dl.productId()));
            if (dl.quantity() == null || dl.quantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive for product " + product.getId());
            }
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPurchaseOrder(po);
            line.setProductId(product.getId());
            line.setQuantity(dl.quantity());
            line.setUnitPrice(dl.unitPrice() != null ? dl.unitPrice() : BigDecimal.ZERO);
            po.getLines().add(line);
        }
        return poRepository.save(po);
    }

    @Transactional
    public PurchaseOrder submit(Long id) {
        PurchaseOrder po = getById(id);
        if (po.getStatus() != OrderStatus.DRAFT) {
            throw new IllegalArgumentException("Only DRAFT orders can be submitted for approval");
        }
        po.setStatus(OrderStatus.PENDING_APPROVAL);
        return poRepository.save(po);
    }

    @Transactional
    public PurchaseOrder approve(Long id, Long approverId) {
        PurchaseOrder po = getById(id);
        if (po.getStatus() != OrderStatus.PENDING_APPROVAL) {
            throw new IllegalArgumentException("Only PENDING_APPROVAL orders can be approved");
        }
        po.setStatus(OrderStatus.APPROVED);
        po.setApprovedBy(approverId);
        return poRepository.save(po);
    }

    @Transactional
    public PurchaseOrder markSent(Long id) {
        PurchaseOrder po = getById(id);
        if (po.getStatus() != OrderStatus.APPROVED) {
            throw new IllegalArgumentException("Only APPROVED orders can be sent");
        }
        po.setStatus(OrderStatus.SENT);
        return poRepository.save(po);
    }

    @Transactional
    public PurchaseOrder markReceived(Long id) {
        PurchaseOrder po = getById(id);
        if (po.getStatus() != OrderStatus.SENT) {
            throw new IllegalArgumentException("Only SENT orders can be received");
        }
        po.setStatus(OrderStatus.RECEIVED);
        return poRepository.save(po);
    }
}
