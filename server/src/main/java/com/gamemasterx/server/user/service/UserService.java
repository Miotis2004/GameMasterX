package com.gamemasterx.server.user.service;

import com.gamemasterx.server.user.model.UserDto;
import com.gamemasterx.server.user.repository.UserRepository;
import com.gamemasterx.server.security.PasswordCodec;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDto createUser(String username, String email, String plaintextPassword) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be empty");
        }
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String passwordHash = PasswordCodec.hash(plaintextPassword);
        UserDto user = new UserDto();
        user.setId(id);
        user.setSchemaVersion(1);
        user.setRevision(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        return userRepository.save(user);
    }

    public Optional<UserDto> findById(String id) {
        return userRepository.findById(id);
    }

    public UserDto updateUser(String id, String username, String email, String plaintextPassword) {
        UserDto existing = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (username != null && !username.isBlank()) {
            existing.setUsername(username);
        }
        if (email != null) {
            existing.setEmail(email);
        }
        if (plaintextPassword != null && !plaintextPassword.isEmpty()) {
            existing.setPasswordHash(PasswordCodec.hash(plaintextPassword));
        }
        existing.setRevision(existing.getRevision() + 1);
        existing.setUpdatedAt(Instant.now());
        return userRepository.save(existing);
    }
}
