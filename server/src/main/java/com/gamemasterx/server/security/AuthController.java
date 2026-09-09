package com.gamemasterx.server.security;

import com.gamemasterx.server.user.model.UserDto;
import com.gamemasterx.server.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid request"));
        }

        Optional<UserDto> userOpt = userRepository.findByUsername(request.getUsername());
        boolean valid = userOpt.isPresent() && PasswordCodec.verify(request.getPassword(), userOpt.get().getPasswordHash());

        if (!valid) {
            // Safe error message, do not reveal whether username exists
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("userId", userOpt.get().getId());
        session.setAttribute("username", userOpt.get().getUsername());

        return ResponseEntity.ok(Map.of("message", "Login successful"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }
}
