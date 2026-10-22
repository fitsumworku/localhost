package com.neueda.leap.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDisputeResponseRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message
) {}
