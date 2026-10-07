package com.neueda.leap.team.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.securities.seed")
public record SecuritySeedProperties(@NotEmpty List<@NotBlank String> resources) {}
