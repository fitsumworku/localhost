package com.neueda.leap.team.controller;

import com.neueda.leap.team.dto.LoginRequest;
import com.neueda.leap.team.dto.RegisterRequest;
import com.neueda.leap.team.dto.UserDto;
import com.neueda.leap.team.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// The /auth endpoints from openapi.yaml
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService service;

    public AuthController(UserService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto register(@RequestBody RegisterRequest req) {
        return service.register(req);
    }

    @PostMapping("/login")
    public UserDto login(@RequestBody LoginRequest req) {
        return service.login(req);
    }

    @PostMapping("/logout")
    public void logout() {
        // No tokens/sessions yet, so there is nothing to clear
    }

    @GetMapping("/me")
    public UserDto me(@RequestParam Long userId) {
        return service.getById(userId);
    }
}
