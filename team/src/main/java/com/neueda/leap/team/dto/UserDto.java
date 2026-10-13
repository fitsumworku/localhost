package com.neueda.leap.team.dto;

import com.neueda.leap.team.entity.User;
import com.neueda.leap.team.entity.enums.*;
import java.time.Instant;

public record UserDto(Long userId, String name, String email, RoleName role,
                      Instant createdDate, Instant updatedDate, UserStatus status) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole().getName(),
                user.getCreatedDate(), user.getUpdatedDate(), user.getStatus());
    }
}
