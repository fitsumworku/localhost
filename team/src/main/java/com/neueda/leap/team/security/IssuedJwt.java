package com.neueda.leap.team.security;

import java.time.Instant;

public record IssuedJwt(String token, Instant expiresAt) {
    @Override public String toString() { return "IssuedJwt[token=REDACTED]"; }
}
