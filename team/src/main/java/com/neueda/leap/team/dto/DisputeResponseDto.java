package com.neueda.leap.team.dto;

import java.time.Instant;

/** An admin's reply on a dispute, visible to the client who filed it. */
public record DisputeResponseDto(Long responseId, Long adminId, String adminName, String message, Instant createdDate) {}
