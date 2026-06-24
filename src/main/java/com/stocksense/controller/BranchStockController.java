package com.stocksense.controller;

import com.stocksense.domain.BranchStock;
import com.stocksense.service.BranchStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches/{branchId}/stock")
public class BranchStockController {

    private final BranchStockService branchStockService;

    @GetMapping
    public List<BranchStock> listByBranch(@PathVariable Long branchId) {
        return branchStockService
                .listByBranch(branchId);
    }

    @GetMapping("/low")
    public List<BranchStock> listLowStock(@PathVariable Long branchId) {
        return branchStockService
                .listLowStock(branchId);
    }

    @GetMapping("/products/{productId}")
    public BranchStock getStock(@PathVariable Long branchId, @PathVariable Long productId) {
        return branchStockService
                .getByBranchAndProduct(branchId, productId);
    }

    @PutMapping("/products/{productId}")
    public BranchStock upsert(@PathVariable Long branchId
            , @PathVariable Long productId
            , @RequestBody Map<String, Integer> body) {

        int qty = body.getOrDefault("quantity", 0);
        int threshold = body.getOrDefault("reorderThreshold", 10);

        return branchStockService
                .upsert(branchId, productId, qty, threshold);
    }

    @PatchMapping("/products/{productId}/adjust")
    public BranchStock adjust(@PathVariable Long branchId, @PathVariable Long productId,
                               @RequestBody Map<String, Integer> body) {

        int delta = body.getOrDefault("delta", 0);

        return branchStockService
                .adjustQuantity(branchId, productId, delta);
    }
}
