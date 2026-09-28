package com.neueda.leap.team.dto;

// Matches the RegisterRequest schema in openapi.yaml
public record RegisterRequest(String name, String email, String password) {}
