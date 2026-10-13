package com.neueda.leap.team.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.fauxnance")
public record FauxnanceProperties(
        @DefaultValue("https://y4t9nq2bqf.execute-api.eu-west-2.amazonaws.com/v1") @NotBlank String baseUrl,
        @DefaultValue("") String apiKey,
        @DefaultValue("10") @Min(1) @Max(20) int timeoutSeconds,
        @DefaultValue("true") boolean allowSynthetic,
        @DefaultValue("1800") @Min(60) long maxQuoteAgeSeconds,
        @DefaultValue("345600") @Min(3600) long maxFxAgeSeconds) {
    // Never include the upstream key in diagnostics.
    @Override public String toString() { return "FauxnanceProperties[baseUrl=" + baseUrl + ", apiKey=REDACTED]"; }
}
