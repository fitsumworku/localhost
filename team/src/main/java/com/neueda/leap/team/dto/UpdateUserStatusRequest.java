package com.neueda.leap.team.dto;

import com.neueda.leap.team.entity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {}
