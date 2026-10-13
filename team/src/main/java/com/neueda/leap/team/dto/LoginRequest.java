package com.neueda.leap.team.dto;

import com.neueda.leap.team.validation.Utf8MaxBytes;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) @Utf8MaxBytes(72) String password) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
