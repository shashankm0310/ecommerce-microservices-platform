package com.ecommerce.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Custom JWT utility for generating and validating HMAC-signed JWTs.
 *
 * @deprecated In favor of Keycloak OAuth2 Resource Server. Keycloak handles token
 * issuance and the gateway validates via JWKS endpoint. Retained for backward
 * compatibility during the migration period.
 */
@Deprecated(since = "1.1.0", forRemoval = false)
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(String secret, long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UUID userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserId(String token) {
        return validateToken(token).getSubject();
    }

    public String getEmail(String token) {
        return validateToken(token).get("email", String.class);
    }

    public String getRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
