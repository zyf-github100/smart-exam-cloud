package com.smart.exam.common.security.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    @Test
    void shouldGenerateAndParseTokenWithCustomClaims() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("test-secret-0123456789abcdef0123456789abcdef");
        properties.setExpirationSeconds(3600L);

        JwtUtil jwtUtil = new JwtUtil(properties);

        String token = jwtUtil.generateToken("user-1", "ADMIN", Map.of("permissions", "USER_LIST"));
        Claims claims = jwtUtil.parse(token);

        assertThat(claims.getSubject()).isEqualTo("user-1");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("permissions", String.class)).isEqualTo("USER_LIST");
        assertThat(jwtUtil.getExpirationSeconds()).isEqualTo(3600L);
    }
}
