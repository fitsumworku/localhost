package com.neueda.leap.team.dto;

import com.neueda.leap.team.entity.UserEntity;

import java.time.LocalDateTime;

// Matches the User schema in openapi.yaml.
// Has no password field, so the password can never be sent back to the client.
public record UserDto(Long userId, String name, String email,
                      LocalDateTime createdDate, String status) {

    public static UserDto from(UserEntity u) {
        return new UserDto(u.getUserId(), u.getName(), u.getEmail(),
                u.getDateCreated(), u.getStatus());
    }
}
