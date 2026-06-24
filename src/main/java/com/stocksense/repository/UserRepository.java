package com.stocksense.repository;

import com.stocksense.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);
    List<AppUser> findAllByTenantId(Long tenantId);
    Optional<AppUser> findByIdAndTenantId(Long id, Long tenantId);
    boolean existsByEmail(String email);
}
