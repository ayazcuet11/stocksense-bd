package com.stocksense.controller;

import com.stocksense.domain.Supplier;
import com.stocksense.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public List<Supplier> listAll() {
        return supplierService
                .listAll();
    }

    @GetMapping("/{id}")
    public Supplier getById(@PathVariable Long id) {
        return supplierService
                .getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Supplier create(@RequestBody Supplier supplier) {
        return supplierService
                .create(supplier);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Supplier update(@PathVariable Long id, @RequestBody Supplier patch) {
        return supplierService
                .update(id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(@PathVariable Long id) {
        supplierService
                .delete(id);
    }
}
