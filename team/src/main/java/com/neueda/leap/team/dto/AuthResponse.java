package com.neueda.leap.team.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInMs, UserDto user) {
}
