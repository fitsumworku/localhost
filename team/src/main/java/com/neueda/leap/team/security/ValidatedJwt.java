package com.neueda.leap.team.security;

import java.time.Instant;

// Only JwtService creates this after cryptographic and claim validation.
public record ValidatedJwt(long userId, long tokenVersion, String tokenId, Instant expiresAt) {}
