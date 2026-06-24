package com.stocksense.controller;

import com.stocksense.domain.Sale;
import com.stocksense.service.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches/{branchId}/sales")
public class SaleController {

    private final SaleService saleService;

    @GetMapping
    public List<Sale> listByBranch(@PathVariable Long branchId) {
        return saleService
                .listByBranch(branchId);
    }

    @GetMapping("/{id}")
    public Sale getById(@PathVariable Long branchId, @PathVariable Long id) {
        return saleService
                .getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Sale record(@PathVariable Long branchId, @RequestBody Sale sale) {

        sale.setBranchId(branchId);

        return saleService
                .record(sale);
    }
}
