package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.AuthResponse;
import com.neueda.leap.team.dto.LoginRequest;
import com.neueda.leap.team.dto.RegisterRequest;
import com.neueda.leap.team.dto.UserDto;
import com.neueda.leap.team.entity.UserEntity;
import com.neueda.leap.team.repository.UserRepository;
import com.neueda.leap.team.security.CustomUserDetailsService;
import com.neueda.leap.team.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    public UserService(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            CustomUserDetailsService userDetailsService,
            JwtService jwtService
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    public UserDto register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        String hashed = passwordEncoder.encode(req.password());
        UserEntity saved = users.save(new UserEntity(req.name(), req.email(), hashed));
        return UserDto.from(saved);
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        UserEntity user = users.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Wrong email or password"));

        String token = jwtService.generateToken(userDetailsService.loadUserByUsername(user.getEmail()));
        return new AuthResponse(token, "Bearer", jwtService.getJwtExpirationMs(), UserDto.from(user));
    }

    public UserDto getById(Long userId) {
        return users.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserDto getByEmail(String email) {
        return users.findByEmail(email)
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }
}
