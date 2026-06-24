package com.stocksense.controller;

import com.stocksense.dto.LoginRequest;
import com.stocksense.dto.LoginResponse;
import com.stocksense.dto.RegisterRequest;
import com.stocksense.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService
                .login(req);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public void register(@Valid @RequestBody RegisterRequest req) {
        authService
                .register(req);
    }
}
