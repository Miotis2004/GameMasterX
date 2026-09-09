package com.gamemasterx.server.admin.service;

import com.gamemasterx.server.admin.model.AdminDto;
import com.gamemasterx.server.admin.repository.AdminRepository;
import com.gamemasterx.server.security.PasswordCodec;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AdminService {

    private final AdminRepository adminRepository;

    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public AdminDto createAdmin(String username, String email, String password) {
        if (adminRepository.count() > 0) {
            throw new IllegalStateException("Administrator already exists");
        }

        validateInput(username, email, password);

        String id = UUID.randomUUID().toString();
        String passwordHash = PasswordCodec.hash(password);

        AdminDto admin = new AdminDto();
        admin.setId(id);
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordHash);
        admin.setCreatedAt(Instant.now());

        return adminRepository.save(admin);
    }

    private void validateInput(String username, String email, String password) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (email == null || email.isBlank() || !email.contains("@") || !email.contains(".")) {
            throw new IllegalArgumentException("Email must be a valid email address");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
    }

    public boolean adminExists() {
        return adminRepository.count() > 0;
    }
}
