package com.stocksense;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stocksense.domain.AppUser;
import com.stocksense.domain.Role;
import com.stocksense.repository.TenantRepository;
import com.stocksense.repository.UserRepository;
import com.stocksense.domain.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();
    @Autowired UserRepository userRepo;
    @Autowired TenantRepository tenantRepo;
    @Autowired PasswordEncoder passwordEncoder;

    private Long tenantId;

    @BeforeEach
    void setup() {
        userRepo.deleteAll();
        tenantRepo.deleteAll();

        Tenant t = new Tenant();
        t.setName("Test Tenant");
        tenantId = tenantRepo.save(t).getId();

        AppUser user = new AppUser();
        user.setTenantId(tenantId);
        user.setName("Test Owner");
        user.setEmail("owner@test.com");
        user.setPasswordHash(passwordEncoder.encode("secret"));
        user.setRole(Role.OWNER);
        userRepo.save(user);
    }

    @Test
    void loginReturnsToken() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "owner@test.com", "password", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "owner@test.com", "password", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/branches"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAccessibleWithToken() throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", "owner@test.com", "password", "secret"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = mapper.readTree(response).get("token").asText();

        mvc.perform(get("/api/branches")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
