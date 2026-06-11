package com.yammer.service;

import com.yammer.dto.LoginRequest;
import com.yammer.dto.LoginResponse;
import com.yammer.entity.UserEntity;
import com.yammer.repository.UserRepository;
import com.yammer.security.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordHasher passwordHasher;

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository
                .findByUsername(request.username())
                .orElseThrow(this::invalidCredentials);

        if (!passwordHasher.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }
        // Transparently upgrade legacy MD5 hashes to BCrypt on a successful login.
        if (passwordHasher.needsUpgrade(user.getPassword())) {
            user.setPassword(passwordHasher.hash(request.password()));
            userRepository.save(user);
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRoles(), user.getClientId());
        return new LoginResponse(token, user.getUsername(), user.getRoles(), user.getClientId());
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
