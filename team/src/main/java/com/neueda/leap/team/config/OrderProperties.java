package com.neueda.leap.team.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.orders")
public record OrderProperties(
        @DefaultValue("25") @Min(1) @Max(25) int batchSize,
        @DefaultValue("180") @Min(60) long leaseSeconds,
        @DefaultValue("300") @Min(30) long closedRetrySeconds,
        @DefaultValue("60") @Min(10) long errorRetrySeconds) {}
