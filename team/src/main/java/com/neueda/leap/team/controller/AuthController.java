package com.neueda.leap.team.controller;

import com.neueda.leap.team.dto.*;
import com.neueda.leap.team.security.AppUserDetails;
import com.neueda.leap.team.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserService service;
    public AuthController(UserService service) { this.service = service; }

    @PostMapping("/register")
    @Operation(summary = "Register a new CLIENT login")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a CLIENT or ADMIN")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.login(req));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Revoke ALL tokens for this login identity")
    public void logout() { service.logout(); }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserDto me(@AuthenticationPrincipal AppUserDetails principal) {
        return service.getById(principal.getUserId());
    }
}
