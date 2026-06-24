package com.stocksense.controller;

import com.stocksense.domain.Product;
import com.stocksense.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

import static java.util.Objects.nonNull;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Product> listAll(@RequestParam(required = false) Long categoryId) {

        if (nonNull(categoryId))
            return productService
                .listByCategory(categoryId);

        return productService
                .listAll();
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id) {
        return productService
                .getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Product create(@RequestBody Product product) {
        return productService
                .create(product);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Product update(@PathVariable Long id, @RequestBody Product patch) {
        return productService
                .update(id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(@PathVariable Long id) {
        productService
                .delete(id);
    }
}
