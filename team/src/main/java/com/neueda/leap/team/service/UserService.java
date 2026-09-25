package com.neueda.leap.team.service;

import com.neueda.leap.team.dto.LoginRequest;
import com.neueda.leap.team.dto.RegisterRequest;
import com.neueda.leap.team.dto.UserDto;
import com.neueda.leap.team.entity.UserEntity;
import com.neueda.leap.team.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository users) {
        this.users = users;
    }

    public UserDto register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        String hashed = encoder.encode(req.password());
        UserEntity saved = users.save(new UserEntity(req.name(), req.email(), hashed));
        return UserDto.from(saved);
    }

    public UserDto login(LoginRequest req) {
        UserEntity user = users.findByEmail(req.email())
                .filter(u -> encoder.matches(req.password(), u.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        return UserDto.from(user);
    }

    public UserDto getById(Long userId) {
        return users.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }
}
