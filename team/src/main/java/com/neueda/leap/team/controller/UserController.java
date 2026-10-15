package com.neueda.leap.team.controller;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.entity.enums.*;
import com.neueda.leap.team.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @GetMapping
    public UserPageResponse listUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) RoleName role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdDate,desc") String sort) {
        return service.listUsers(status, role, page, size, sort);
    }

    @GetMapping("/{userId}")
    public UserDto getUserById(@PathVariable Long userId) { return service.getById(userId); }

    @PatchMapping("/{userId}")
    public UserDto updateUserProfile(@PathVariable Long userId, @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(userId, request);
    }

    @PatchMapping("/{userId}/status")
    public UserDto updateUserStatus(@PathVariable Long userId, @Valid @RequestBody UpdateUserStatusRequest request) {
        return service.updateStatus(userId, request);
    }

    @PostMapping("/{userId}/revoke-sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSessions(@PathVariable Long userId) { service.revokeSessions(userId); }
}
