package com.yammer.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    /** The repo-committed development fallback — must never sign tokens in a real deployment. */
    private static final String INSECURE_DEFAULT_SECRET = "dev-secret-change-me-please-32-bytes-minimum!!";

    /** HS256 needs at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private final Environment environment;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key;
    /** jjwt parsers are thread-safe and meant to be built once and reused. */
    private JwtParser parser;

    @PostConstruct
    void init() {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes; set JWT_SECRET.");
        }
        // Fail fast if the publicly-known dev fallback is used outside an explicit local/dev profile —
        // otherwise anyone could forge a SUPER token with the value committed to the repo.
        if (INSECURE_DEFAULT_SECRET.equals(secret) && !isDevProfile()) {
            throw new IllegalStateException(
                    "JWT_SECRET is unset, so the insecure committed default would sign tokens. "
                            + "Set JWT_SECRET to a strong per-environment secret.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.parser = Jwts.parser().verifyWith(key).build();
    }

    private boolean isDevProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("local") || p.equalsIgnoreCase("dev"));
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
        return parser.parseSignedClaims(token).getPayload();
    }
}
