package com.gamemasterx.server.admin.service;

import com.gamemasterx.server.user.model.UserDto;
import com.gamemasterx.server.user.repository.UserRepository;
import com.gamemasterx.server.security.PasswordCodec;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto createAdmin(String username, String email, String password) {
        if (userRepository.existsByGlobalAdminTrue()) {
            throw new IllegalStateException("Administrator already exists");
        }

        validateInput(username, email, password);

        String id = UUID.randomUUID().toString();
        String passwordHash = PasswordCodec.hash(password);

        UserDto admin = new UserDto();
        admin.setId(id);
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPasswordHash(passwordHash);
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        admin.setSchemaVersion(1);
        admin.setRevision(1);
        admin.setGlobalAdmin(true);

        return userRepository.save(admin);
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
        return userRepository.existsByGlobalAdminTrue();
    }
}
