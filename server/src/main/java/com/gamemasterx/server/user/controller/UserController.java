package com.gamemasterx.server.user.controller;

import com.gamemasterx.server.user.model.CreateUserRequest;
import com.gamemasterx.server.user.model.UpdateUserRequest;
import com.gamemasterx.server.user.model.UserDto;
import com.gamemasterx.server.user.model.UserResponse;
import com.gamemasterx.server.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody CreateUserRequest request) {
        UserDto created = userService.createUser(request.getUsername(), request.getEmail(), request.getPassword());
        return new ResponseEntity<>(new UserResponse(created), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String id) {
        return userService.findById(id)
                .map(dto -> ResponseEntity.ok(new UserResponse(dto)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        try {
            UserDto updated = userService.updateUser(id, request.getUsername(), request.getEmail(), request.getPassword());
            return ResponseEntity.ok(new UserResponse(updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
    }
}
