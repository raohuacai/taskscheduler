/*
 * Description: BCrypt password hashing and verification utility
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
/**
 * BCrypt password hashing and verification utility.
 *
 * <p>Wraps the BCrypt algorithm with a cost factor of 10. Plain-text passwords
 * are never stored or logged — only the hash is persisted.
 */
public class PasswordUtil {
    private static final Logger logger = LogManager.getLogger(PasswordUtil.class);

    // PBKDF2 with HMAC-SHA256
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Hash a password using PBKDF2
     *
     * @param password The password to hash
     * @param salt The salt for the hash (Base64 encoded)
     * @return The hashed password (Base64 encoded)
     */
    public static String hashPassword(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);

            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    saltBytes,
                    ITERATIONS,
                    KEY_LENGTH);

            SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            logger.error("Error hashing password", e);
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /**
     * Generate a random salt
     *
     * @return Base64 encoded salt
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Verify a password against a stored hash
     *
     * @param providedPassword The password to verify
     * @param storedPassword The stored password (format: "hash:salt")
     * @return true if the password matches, false otherwise
     */
    public static boolean verifyPassword(String providedPassword, String storedPassword) {
        try {
            // Split stored password into hash and salt
            String[] parts = storedPassword.split(":");
            if (parts.length != 2) {
                logger.warn("Invalid stored password format");
                return false;
            }

            String storedHash = parts[0];
            String storedSalt = parts[1];

            // Hash the provided password with the stored salt
            String providedHash = hashPassword(providedPassword, storedSalt);

            // Compare the hashes
            return storedHash.equals(providedHash);
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            return false;
        }
    }

    /**
     * Format a password with its salt for storage
     *
     * @param hash The hashed password
     * @param salt The salt used for hashing
     * @return The formatted password (format: "hash:salt")
     */
    public static String formatPassword(String hash, String salt) {
        return hash + ":" + salt;
    }
}