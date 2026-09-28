package com.neueda.leap.team.dto;

// Matches the LoginRequest schema in openapi.yaml
public record LoginRequest(String email, String password) {}
