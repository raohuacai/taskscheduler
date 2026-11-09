/*
 * Description: Contract for user authentication and RBAC authorisation
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;
/**
 * Contract for user authentication and API-level authorisation.
 *
 * <p>Implementations validate credentials, issue JWT tokens, verify tokens on
 * subsequent requests, and enforce role-based access control (RBAC) for write
 * operations on restricted API paths.
 *
 * @see com.ts.service.impl.AuthServiceImpl
 */
public interface AuthService {

    /**
     * Authenticate a user with username and password
     *
     * @param username The username
     * @param password The password
     * @return JWT token if authentication is successful, null otherwise
     */
    String authenticate(String username, String password);

    /**
     * Validate a JWT token
     *
     * @param token The JWT token
     * @return true if the token is valid, false otherwise
     */
    boolean validateToken(String token);

    /**
     * Extract username from JWT token
     *
     * @param token The JWT token
     * @return The username
     */
    String getUsernameFromToken(String token);

    /**
     * Extract role from JWT token
     *
     * @param token The JWT token
     * @return The role (ADMIN or USER), defaults to USER if not present
     */
    String getRoleFromToken(String token);

    /**
     * Check if a user has permission to access a path
     *
     * @param username The username
     * @param path     The API path to access
     * @return true if the user has permission, false otherwise
     */
    boolean hasPermission(String username, String path);
}
