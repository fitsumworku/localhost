package com.neueda.leap.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDisputeRequest(
        @NotBlank(message = "Dispute type is required")
        @Size(min = 3, max = 50, message = "Dispute type must be between 3 and 50 characters")
        String disputeType,
        
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description
) {}
