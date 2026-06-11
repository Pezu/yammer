package com.yammer.security;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

/**
 * Single home for password hashing and verification. New hashes are BCrypt
 * (salted, work-factored). Historical hashes were unsalted MD5 hex (matching
 * Postgres {@code md5()}); those are still accepted on login and
 * {@link #needsUpgrade(String)} flags them so callers can re-hash to BCrypt.
 */
@Component
@RequiredArgsConstructor
public class PasswordHasher {

    /** A 32-char lowercase hex string is a legacy MD5 digest. */
    private static final int MD5_HEX_LENGTH = 32;

    private final PasswordEncoder encoder;

    /** Hash a raw password for storage (always BCrypt). */
    public String hash(String raw) {
        return encoder.encode(raw);
    }

    /** True if {@code raw} matches the stored hash, whether it's BCrypt or a legacy MD5 hex. */
    public boolean matches(String raw, String stored) {
        if (stored == null) {
            return false;
        }
        if (isLegacyMd5(stored)) {
            return md5Hex(raw).equalsIgnoreCase(stored);
        }
        return encoder.matches(raw, stored);
    }

    /** True if the stored hash is a legacy MD5 digest that should be re-hashed to BCrypt. */
    public boolean needsUpgrade(String stored) {
        return isLegacyMd5(stored);
    }

    private boolean isLegacyMd5(String stored) {
        return stored != null
                && stored.length() == MD5_HEX_LENGTH
                && stored.chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }

    private String md5Hex(String raw) {
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }
}
