package com.ewallet.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and validates JWT access tokens (HS256).
 *
 * <p><b>Claims layout:</b>
 * <pre>
 *   sub  — user UUID
 *   role — Spring Security role string
 *   jti  — unique token ID (enables per-token revocation if needed)
 *   iat  — issued-at
 *   exp  — expiry
 * </pre>
 *
 * <p>The signing key is derived from the {@code jwt.secret} property and must
 * be at least 256 bits for HS256. In production, rotate via Vault or AWS Secrets
 * Manager and implement a key-version claim for zero-downtime rotation.
 */
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    // ── generation ──────────────────────────────────────────────────────────

    public String generateAccessToken(UUID userId, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of("role", role))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // ── validation ──────────────────────────────────────────────────────────

    public Claims validateAndExtract(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(validateAndExtract(token).getSubject());
    }

    public String extractRole(String token) {
        return validateAndExtract(token).get("role", String.class);
    }

    public boolean isValid(String token) {
        try {
            validateAndExtract(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
}
