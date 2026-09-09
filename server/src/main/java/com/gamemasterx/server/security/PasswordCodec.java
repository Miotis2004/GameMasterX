package com.gamemasterx.server.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Adaptive password hashing codec using BCrypt.
 * Provides secure defaults and prevents plaintext persistence or logging.
 */
public final class PasswordCodec {

    private static final int BCRYPT_STRENGTH = 12; // secure default, work factor 12
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    private PasswordCodec() {
        // utility class
    }

    /**
     * Hashes a plaintext password using BCrypt with secure defaults.
     * Never logs or returns plaintext.
     *
     * @param plaintextPassword plaintext password, must not be null
     * @return BCrypt hash string
     * @throws IllegalArgumentException if input is null or empty
     */
    public static String hash(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isEmpty()) {
            throw new IllegalArgumentException("Password must not be null or empty");
        }
        return ENCODER.encode(plaintextPassword);
    }

    /**
     * Verifies a plaintext password against a stored BCrypt hash.
     *
     * @param plaintextPassword candidate password
     * @param storedHash        previously generated BCrypt hash
     * @return true if password matches hash
     */
    public static boolean verify(String plaintextPassword, String storedHash) {
        if (plaintextPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        return ENCODER.matches(plaintextPassword, storedHash);
    }

    /**
     * Exposes the encoder for integration with Spring Security if needed.
     */
    public static PasswordEncoder encoder() {
        return ENCODER;
    }
}
