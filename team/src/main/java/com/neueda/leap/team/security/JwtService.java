package com.neueda.leap.team.security;

import com.neueda.leap.team.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(properties.secret()); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT_SECRET_BASE64 must be valid Base64."); }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET_BASE64 must decode to at least 32 random bytes.");
        this.signingKey = Keys.hmacShaKeyFor(bytes);
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.issuer())
                .requireAudience(properties.audience())
                .clock(() -> Date.from(clock.instant()))
                .clockSkewSeconds(0)
                .build();
    }

    public IssuedJwt generateToken(User user) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plusMillis(properties.expirationMs()).truncatedTo(ChronoUnit.SECONDS);
        String token = Jwts.builder()
                .header().type("JWT").and()
                .subject(user.getId().toString())
                .issuer(properties.issuer())
                .audience().add(properties.audience()).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now)).notBefore(Date.from(now)).expiration(Date.from(expiresAt))
                .claim("ver", user.getTokenVersion())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedJwt(token, expiresAt);
    }

    public ValidatedJwt parseAndValidate(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) throw invalid();
        // Exactly one parse verifies the signature, issuer/audience and exp/nbf.
        // Require our chosen algorithm before using the verified claims.
        Jws<Claims> signed = parser.parseSignedClaims(token);
        if (!"HS256".equals(signed.getHeader().getAlgorithm())) throw invalid();
        Claims claims = signed.getPayload();
        if (claims.getSubject() == null || claims.getId() == null || claims.getId().isBlank()
                || claims.getExpiration() == null || claims.getIssuedAt() == null
                || claims.getNotBefore() == null) throw invalid();
        Instant issuedAt = claims.getIssuedAt().toInstant();
        Instant expiresAt = claims.getExpiration().toInstant();
        if (!expiresAt.isAfter(clock.instant()) || !expiresAt.isAfter(issuedAt)
                || issuedAt.isAfter(clock.instant().plusSeconds(30))
                || claims.getNotBefore().toInstant().isAfter(expiresAt)
                || expiresAt.isAfter(issuedAt.plusMillis(properties.expirationMs()))) throw invalid();
        Long version = claims.get("ver", Long.class);
        long userId;
        try { userId = Long.parseLong(claims.getSubject()); }
        catch (NumberFormatException e) { throw invalid(); }
        if (userId <= 0 || version == null || version < 0) throw invalid();
        return new ValidatedJwt(userId, version, claims.getId(), expiresAt);
    }

    private JwtException invalid() { return new JwtException("Invalid access token"); }
}
