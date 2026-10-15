package com.neueda.leap.team.dto;

import com.neueda.leap.team.entity.enums.DisputeStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateDisputeStatusRequest(@NotNull DisputeStatus disputeStatus) {}
