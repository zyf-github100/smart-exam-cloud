package com.smart.exam.gateway.filter;

import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.security.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_LIST = List.of(
            "/api/v1/auth/login",
            "/actuator"
    );
    private static final List<String> TRUSTED_AUTH_HEADERS = List.of(
            "X-User-Id",
            "X-Role",
            "X-Permissions"
    );

    private final JwtUtil jwtUtil;

    public JwtAuthGlobalFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, ErrorCode.UNAUTHORIZED.getMessage());
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtUtil.parse(token);
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange, ErrorCode.UNAUTHORIZED.getMessage());
        }
        String userId = claims.getSubject();
        Object roleValue = claims.get("role");
        String role = roleValue == null ? null : String.valueOf(roleValue).trim();
        String permissions = normalizePermissions(claims.get("permissions"));
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(role)) {
            return unauthorized(exchange, ErrorCode.UNAUTHORIZED.getMessage());
        }

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        builder.headers(headers -> {
            TRUSTED_AUTH_HEADERS.forEach(headers::remove);
            headers.set("X-User-Id", userId);
            headers.set("X-Role", role.toUpperCase(Locale.ROOT));
            if (StringUtils.hasText(permissions)) {
                headers.set("X-Permissions", permissions);
            }
        });

        ServerHttpRequest request = builder.build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isWhitePath(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        String body = "{\"code\":40100,\"message\":\"" + message + "\",\"data\":null}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    private String normalizePermissions(Object permissionsClaim) {
        if (permissionsClaim == null) {
            return "";
        }
        if (permissionsClaim instanceof Collection<?> values) {
            return values.stream()
                    .filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
                    .map(item -> String.valueOf(item).trim().toUpperCase(Locale.ROOT))
                    .distinct()
                    .collect(Collectors.joining(","));
        }
        String raw = String.valueOf(permissionsClaim).trim();
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw;
    }
}
