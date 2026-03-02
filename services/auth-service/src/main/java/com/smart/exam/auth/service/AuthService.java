package com.smart.exam.auth.service;

import com.smart.exam.auth.dto.LoginRequest;
import com.smart.exam.common.core.error.BizException;
import com.smart.exam.common.core.error.ErrorCode;
import com.smart.exam.common.security.jwt.JwtUtil;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final JwtUtil jwtUtil;
    private final Map<String, DemoUser> users = new HashMap<>();

    public AuthService(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
        users.put("admin", new DemoUser("10001", "admin", "123456", "ADMIN"));
        users.put("teacher1", new DemoUser("20001", "teacher1", "123456", "TEACHER"));
        users.put("stu1", new DemoUser("30001", "stu1", "123456", "STUDENT"));
    }

    public Map<String, Object> login(LoginRequest request) {
        DemoUser user = users.get(request.getUsername());
        if (user == null || !user.password().equals(request.getPassword())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.id(), user.role(), Map.of("username", user.username()));
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("expiresIn", jwtUtil.getExpirationSeconds());
        payload.put("user", Map.of(
                "id", user.id(),
                "username", user.username(),
                "role", user.role()
        ));
        return payload;
    }

    private record DemoUser(String id, String username, String password, String role) {
    }
}

