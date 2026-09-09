package com.gamemasterx.server.admin.controller;

import com.gamemasterx.server.admin.model.AdminDto;
import com.gamemasterx.server.admin.model.AdminSetupRequest;
import com.gamemasterx.server.admin.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminSetupController {

    private final AdminService adminService;

    public AdminSetupController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/setup")
    public ResponseEntity<Map<String, Object>> setupAdmin(@RequestBody AdminSetupRequest request) {
        try {
            AdminDto created = adminService.createAdmin(request.getUsername(), request.getEmail(), request.getPassword());
            Map<String, Object> body = Map.of(
                "id", created.getId(),
                "username", created.getUsername(),
                "email", created.getEmail(),
                "createdAt", created.getCreatedAt().toString()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
        }
    }
}
