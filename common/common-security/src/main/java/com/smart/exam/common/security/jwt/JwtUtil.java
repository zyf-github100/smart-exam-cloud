package com.smart.exam.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class JwtUtil {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String LEGACY_INSECURE_SECRET = "smart-exam-cloud-secret-key-must-be-at-least-32-bytes";

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("security.jwt.secret must be configured (set JWT_SECRET)");
        }
        String normalizedSecret = secret.trim();
        if (LEGACY_INSECURE_SECRET.equals(normalizedSecret)) {
            throw new IllegalStateException("security.jwt.secret uses legacy insecure default value, please rotate");
        }

        byte[] keyBytes = resolveSecretBytes(normalizedSecret);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("security.jwt.secret must be at least 32 bytes after decoding");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveSecretBytes(String secret) {
        if (secret.matches("^[A-Za-z0-9+/=]+$") && secret.length() % 4 == 0) {
            try {
                return Decoders.BASE64.decode(secret);
            } catch (Exception ignored) {
                return secret.getBytes(StandardCharsets.UTF_8);
            }
        }
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(String userId, String role, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(properties.getExpirationSeconds());
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(userId)
                .id(jti)
                .claim("role", role)
                .claims(extraClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return properties.getExpirationSeconds();
    }
}
