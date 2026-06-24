package com.stocksense.controller;

import com.stocksense.domain.Category;
import com.stocksense.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<Category> listAll() {
        return categoryService
                .listAll();
    }

    @GetMapping("/{id}")
    public Category getById(@PathVariable Long id) {
        return categoryService
                .getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Category create(@RequestBody Category category) {
        return categoryService
                .create(category);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','MANAGER')")
    public Category update(@PathVariable Long id, @RequestBody Category patch) {
        return categoryService
                .update(id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(@PathVariable Long id) {
        categoryService
                .delete(id);
    }
}
