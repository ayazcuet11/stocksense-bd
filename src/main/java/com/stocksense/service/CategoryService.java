package com.stocksense.service;

import com.stocksense.domain.Category;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.CategoryRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> listAll() {
        return categoryRepository.findAllByTenantId(TenantContext.get());
    }

    public Category getById(Long id) {
        return categoryRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    @Transactional
    public Category create(Category category) {
        category.setTenantId(TenantContext.get());
        return categoryRepository.save(category);
    }

    @Transactional
    public Category update(Long id, Category patch) {
        Category existing = getById(id);
        if (patch.getName() != null) existing.setName(patch.getName());
        return categoryRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepository.delete(getById(id));
    }
}
