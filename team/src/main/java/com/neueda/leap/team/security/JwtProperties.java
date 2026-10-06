package com.neueda.leap.team.security;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(@NotBlank String secret,
                            @Min(60_000) @Max(3_600_000) long expirationMs,
                            @NotBlank String issuer, @NotBlank String audience) {
    @Override public String toString() { return "JwtProperties[secret=REDACTED]"; }
}
