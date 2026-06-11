package com.yammer.controller;

import com.yammer.dto.LoginRequest;
import com.yammer.dto.LoginResponse;
import com.yammer.security.LoginAttemptService;
import com.yammer.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptService loginAttempts;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ipKey = "ip:" + clientIp(http);
        String userKey = "user:" + request.username();
        if (loginAttempts.isBlocked(ipKey) || loginAttempts.isBlocked(userKey)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Try again later.");
        }
        try {
            LoginResponse response = authService.login(request);
            loginAttempts.recordSuccess(ipKey);
            loginAttempts.recordSuccess(userKey);
            return response;
        } catch (ResponseStatusException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                loginAttempts.recordFailure(ipKey);
                loginAttempts.recordFailure(userKey);
            }
            throw e;
        }
    }

    /** Prefer the first X-Forwarded-For hop (Cloud Run / proxy), else the socket address. */
    private String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
