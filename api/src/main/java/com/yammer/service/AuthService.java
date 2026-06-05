package com.yammer.service;

import com.yammer.dto.LoginRequest;
import com.yammer.dto.LoginResponse;
import com.yammer.entity.UserEntity;
import com.yammer.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository
                .findByUsername(request.username())
                .orElseThrow(this::invalidCredentials);

        // Passwords are stored as MD5 hex (matches Postgres md5()).
        String hashed = DigestUtils.md5DigestAsHex(request.password().getBytes(StandardCharsets.UTF_8));
        if (!hashed.equals(user.getPassword())) {
            throw invalidCredentials();
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getClientId());
        return new LoginResponse(token, user.getUsername(), user.getRoles(), user.getClientId());
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
