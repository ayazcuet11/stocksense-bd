package com.stocksense.service;

import com.stocksense.domain.Branch;
import com.stocksense.exception.ResourceNotFoundException;
import com.stocksense.repository.BranchRepository;
import com.stocksense.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public List<Branch> listAll() {
        return branchRepository.findAllByTenantId(TenantContext.get());
    }

    public Branch getById(Long id) {
        return branchRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + id));
    }

    @Transactional
    public Branch create(Branch branch) {
        branch.setTenantId(TenantContext.get());
        return branchRepository.save(branch);
    }

    @Transactional
    public Branch update(Long id, Branch patch) {
        Branch existing = getById(id);
        if (patch.getName() != null) existing.setName(patch.getName());
        if (patch.getAddress() != null) existing.setAddress(patch.getAddress());
        if (patch.getCity() != null) existing.setCity(patch.getCity());
        if (patch.getPhone() != null) existing.setPhone(patch.getPhone());
        return branchRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Branch branch = getById(id);
        branchRepository.delete(branch);
    }
}
