package com.yammer.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key;

    @PostConstruct
    void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issue a signed JWT. The subject is the username; the token also carries the user's
     * roles and (for non-SUPER users) the id of the client they belong to.
     */
    public String generateToken(String username, List<String> roles, UUID clientId) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs));
        if (clientId != null) {
            builder.claim("clientId", clientId.toString());
        }
        return builder.signWith(key).compact();
    }

    /** Verify the signature/expiry and return the token's claims. Throws if invalid. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
