/*
 * Description: HMAC-SHA256 JWT token generation and validation utility
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
/**
 * JWT token generation and validation utility.
 *
 * <p>Uses HMAC-SHA256 signing. Token claims include the username, role, and
 * expiry time. {@link #validateToken(String)} returns {@code null} rather than
 * throwing on invalid or expired tokens.
 */
public class JwtUtil {
    private static final Logger logger = LogManager.getLogger(JwtUtil.class);

    // Secret key for JWT signing - in production, this should be externalized and secured
    private static final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS512);

    // Token validity period in milliseconds (24 hours)
    private static final long JWT_TOKEN_VALIDITY = 24 * 60 * 60 * 1000;

    // Token issuer name
    private static final String ISSUER = "workflow-engine";

    /**
     * Generate a JWT token for a user
     *
     * @param username The username
     * @return JWT token
     */
    public static String generateToken(String username) {
        return generateToken(username, new HashMap<>());
    }

    /**
     * Generate a JWT token for a user with additional claims
     *
     * @param username The username
     * @param additionalClaims Additional claims to include in the token
     * @return JWT token
     */
    public static String generateToken(String username, Map<String, Object> additionalClaims) {
        long currentTime = System.currentTimeMillis();
        Date issuedAt = new Date(currentTime);
        Date expiration = new Date(currentTime + JWT_TOKEN_VALIDITY);

        return Jwts.builder()
                .setClaims(additionalClaims)
                .setSubject(username)
                .setIssuer(ISSUER)
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Validate a JWT token
     *
     * @param token The JWT token
     * @return true if the token is valid, false otherwise
     */
    public static boolean validateToken(String token) {
        try {
            // Verify signature and check if expired
            Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token);

            return !isTokenExpired(token);
        } catch (Exception e) {
            logger.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract username from JWT token
     *
     * @param token The JWT token
     * @return The username
     */
    public static String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * Extract expiration date from JWT token
     *
     * @param token The JWT token
     * @return The expiration date
     */
    public static Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * Extract specific claim from JWT token
     *
     * @param token The JWT token
     * @param claimsResolver Function to resolve the claim
     * @return The claim value
     */
    public static <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from JWT token
     *
     * @param token The JWT token
     * @return All claims
     */
    private static Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Check if JWT token is expired
     *
     * @param token The JWT token
     * @return true if the token is expired, false otherwise
     */
    private static boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }
}