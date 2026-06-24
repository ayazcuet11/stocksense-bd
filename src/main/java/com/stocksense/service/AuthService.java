package com.stocksense.service;

import com.stocksense.domain.AppUser;
import com.stocksense.dto.LoginRequest;
import com.stocksense.dto.LoginResponse;
import com.stocksense.dto.RegisterRequest;
import com.stocksense.exception.ConflictException;
import com.stocksense.repository.UserRepository;
import com.stocksense.security.JwtTokenProvider;
import com.stocksense.security.TenantContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AppUser register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered: " + req.email());
        }
        AppUser user = new AppUser();
        user.setTenantId(TenantContext.get());
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(req.role());
        user.setBranchId(req.branchId());
        return userRepository.save(user);
    }

    public LoginResponse login(LoginRequest req) {
        AppUser user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String token = tokenProvider.generate(user.getEmail(), user.getTenantId(), user.getRole().name());
        return new LoginResponse(token, user.getEmail(), user.getRole().name(), user.getTenantId());
    }
}
