package com.stocksense.controller;

import com.stocksense.domain.PurchaseOrder;
import com.stocksense.dto.reorder.PurchaseOrderView;
import com.stocksense.repository.UserRepository;
import com.stocksense.service.PurchaseOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService poService;
    private final UserRepository userRepository;

    @GetMapping
    public List<PurchaseOrder> listAll() {
        return poService
                .listAll();
    }

    /** Detailed view with supplier/product names, line items and totals (used by the Orders screen). */
    @GetMapping("/detailed")
    public List<PurchaseOrderView> listDetailed() {
        return poService
                .listViews(null);
    }

    @GetMapping("/pending")
    public List<PurchaseOrder> listPending() {
        return poService
                .listPendingApproval();
    }

    @GetMapping("/{id}")
    public PurchaseOrder getById(@PathVariable Long id) {
        return poService
                .getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public PurchaseOrder create(@RequestBody PurchaseOrder po) {
        return poService
                .create(po);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public PurchaseOrder submit(@PathVariable Long id) {
        return poService
                .submit(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public PurchaseOrder approve(@PathVariable Long id, Principal principal) {

        var approver = userRepository
                .findByEmail(principal.getName())
                .orElseThrow();

        return poService
                .approve(id, approver.getId());
    }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public PurchaseOrder markSent(@PathVariable Long id) {
        return poService
                .markSent(id);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER','STAFF')")
    public PurchaseOrder markReceived(@PathVariable Long id) {
        return poService
                .markReceived(id);
    }
}
