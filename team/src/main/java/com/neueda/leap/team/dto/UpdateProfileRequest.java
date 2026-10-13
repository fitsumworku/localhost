package com.neueda.leap.team.dto;

import jakarta.validation.constraints.*;

// Only display-name changes are in this module. Credential changes need a separate flow.
public record UpdateProfileRequest(@NotBlank @Size(max = 100) String name) {
    public UpdateProfileRequest { name = name == null ? null : name.strip(); }
}
