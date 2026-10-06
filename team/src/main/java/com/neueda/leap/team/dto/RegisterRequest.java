package com.neueda.leap.team.dto;

import com.neueda.leap.team.validation.Utf8MaxBytes;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 12, max = 72) @Utf8MaxBytes(72) String password) {
    public RegisterRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        // Passwords must never be trimmed or normalized.
    }
    @Override public String toString() { return "RegisterRequest[REDACTED]"; }
}
