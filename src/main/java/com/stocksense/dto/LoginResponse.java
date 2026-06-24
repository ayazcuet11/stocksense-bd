package com.stocksense.dto;

public record LoginResponse(String token, String email, String role, Long tenantId) {}
