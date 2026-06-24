package com.stocksense.controller;

import com.stocksense.domain.Branch;
import com.stocksense.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public List<Branch> listAll() {
        return branchService
                .listAll();
    }

    @GetMapping("/{id}")
    public Branch getById(@PathVariable Long id) {
        return branchService
                .getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public Branch create(@Valid @RequestBody Branch branch) {
        return branchService
                .create(branch);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public Branch update(@PathVariable Long id, @RequestBody Branch patch) {
        return branchService
                .update(id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(@PathVariable Long id) {
        branchService
                .delete(id);
    }
}
