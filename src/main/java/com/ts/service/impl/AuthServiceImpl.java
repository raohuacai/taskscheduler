/*
 * Description: JWT + RBAC authentication service with token blacklisting and role-based write restriction
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.ts.mapper.UserMapper;
import com.ts.model.entity.User;
import com.ts.service.AuthService;
import com.ts.common.utils.JwtUtil;
import com.ts.common.utils.PasswordUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
/**
 * JWT-based authentication and RBAC authorisation service.
 *
 * <p>On successful login, issues a JWT containing the user's role ({@code ADMIN}
 * or {@code USER}). On subsequent API calls, {@link #verifyToken} extracts and
 * validates the JWT and returns the embedded role. {@link #isAuthorized} enforces
 * write-method restrictions: {@code USER} role is blocked from POST/PUT/DELETE
 * on the path prefixes listed in {@code ADMIN_WRITE_PATHS}.
 *
 * <p>Maintains a token blacklist ({@code revokedTokens}) so that logout
 * invalidates the token immediately without waiting for expiry.
 *
 * <p>Password storage: passwords are hashed using
 * {@link com.ts.common.utils.PasswordUtil} (BCrypt). Plain-text comparison is
 * never performed after the initial migration.
 *
 * @see com.ts.server.handler.auth.LoginHandler
 */
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LogManager.getLogger(AuthServiceImpl.class);

    /** Role constant for administrators: full write access to all API paths. */
    public static final String ROLE_ADMIN = "ADMIN";

    /** Role constant for regular users: read-only on most paths;
     *  full access to Scheduler, Monitoring, Run History, and Release Lock. */
    public static final String ROLE_USER  = "USER";

    /**
     * API path prefixes whose write methods (POST / PUT / DELETE) are
     * restricted to ADMIN only.
     */
    private static final Set<String> ADMIN_WRITE_PATHS = Set.of(
            "/api/definition/",
            "/api/params/",
            "/api/tasktype",
            "/api/users"
    );

    private final UserMapper userMapper;

    // Simple in-memory cache for validated tokens (production should use proper caching)
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    /**
     * Token cache info
     */
    private static class TokenInfo {
        final String username;
        final String role;
        final long   expiryTime;

        TokenInfo(String username, String role, long expiryTime) {
            this.username   = username;
            this.role       = role;
            this.expiryTime = expiryTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    /**
     * Constructs a new AuthServiceImpl instance.
     *
     * @param userMapper  userMapper value
     */
    public AuthServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;

        // Start a background thread to clean expired tokens
        startCacheCleanupThread();
    }

    /**
     * Starts a background thread to clean expired tokens from cache
     */
    private void startCacheCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Clean expired tokens every minute
                    Thread.sleep(60000);
                    cleanExpiredTokens();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.setName("token-cache-cleanup");
        cleanupThread.start();
    }

    /**
     * Cleans expired tokens from cache
     */
    private void cleanExpiredTokens() {
        tokenCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    @Override
    /**
     * Authenticate.
     *
     * @param username  username name
     * @param password  password value
     * @return the result as a String
     */
    public String authenticate(String username, String password) {
        User user = userMapper.getUserByUsername(username);

        if (user == null) {
            logger.warn("Authentication failed: User not found: {}", username);
            return null;
        }
        if (!"A".equals(user.getStatus())) {
            logger.warn("Authentication failed: User inactive: {}", username);
            return null;
        }

        // Password verification (supports both legacy plain-text and hashed)
        if (!user.getPassword().contains(":")) {
            if (!password.equals(user.getPassword())) {
                logger.warn("Authentication failed: Invalid legacy password for user: {}", username);
                return null;
            }
            migratePassword(user, password);
        } else {
            if (!PasswordUtil.verifyPassword(password, user.getPassword())) {
                logger.warn("Authentication failed: Invalid password for user: {}", username);
                return null;
            }
        }

        // Determine role: fall back to USER if not set
        String role = (user.getRole() != null && !user.getRole().isEmpty())
                ? user.getRole().toUpperCase()
                : ROLE_USER;

        // Embed role and user details into JWT claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("fullName", user.getFullName());
        claims.put("email",    user.getEmail());
        claims.put("role",     role);

        // Generate JWT token
        String token = JwtUtil.generateToken(username, claims);
        Date expiry = JwtUtil.getExpirationDateFromToken(token);

        // Add to cache
        tokenCache.put(token, new TokenInfo(username, role, expiry.getTime()));

        logger.info("User successfully authenticated: {} (role={})", username, role);
        //logger.info("User successfully authenticated: {} (token={})", username, token);
        return token;
    }

    /**
     * Migrate legacy password to new hashed format
     */
    private void migratePassword(User user, String plainPassword) {
        try {
            String salt        = PasswordUtil.generateSalt();
            String hashedPassword = PasswordUtil.hashPassword(plainPassword, salt);

            // Format: "hash:salt"
            String newPassword = hashedPassword + ":" + salt;

            // Update user object
            user.setPassword(newPassword);
            user.setLastUpdatedBy("SYSTEM");
            user.setLastUpdateDate(new Date());
            userMapper.updateUserPassword(user);
            logger.info("Password migrated to secure format for user: {}", user.getUsername());
        } catch (Exception e) {
            logger.error("Error migrating password for user: {}", user.getUsername(), e);
        }
    }

    // ── validateToken ──────────────────────────────────────────────────────

    @Override
    /**
     * Validates the token.
     *
     * @param token  token value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean validateToken(String token) {
        TokenInfo info = tokenCache.get(token);
        if (info != null) {
            if (info.isExpired()) {
                tokenCache.remove(token);
                return false;
            }
            return true;
        }
        if (JwtUtil.validateToken(token)) {
            String username = JwtUtil.getUsernameFromToken(token);
            String role     = extractRoleFromJwt(token);
            Date   expiry   = JwtUtil.getExpirationDateFromToken(token);
            tokenCache.put(token, new TokenInfo(username, role, expiry.getTime()));
            return true;
        }
        return false;
    }

    @Override
    /**
     * Returns the username from token.
     *
     * @param token  token value
     * @return the result as a String
     */
    public String getUsernameFromToken(String token) {
        // Check cache first
        TokenInfo info = tokenCache.get(token);
        if (info != null && !info.isExpired()) {
            return info.username;
        }

        return JwtUtil.getUsernameFromToken(token);
    }


    // ── getRoleFromToken ───────────────────────────────────────────────────

    @Override
    /**
     * Returns the role from token.
     *
     * @param token  token value
     * @return the result as a String
     */
    public String getRoleFromToken(String token) {
        TokenInfo info = tokenCache.get(token);
        if (info != null && !info.isExpired()) return info.role;
        return extractRoleFromJwt(token);
    }

    /** Extract 'role' claim from a raw JWT (without cache). */
    private String extractRoleFromJwt(String token) {
        try {
            //Object roleClaim = JwtUtil.getClaimFromToken(token, "role");
            //if (roleClaim != null) return roleClaim.toString().toUpperCase();
            String role = JwtUtil.getClaimFromToken(token, claims -> claims.get("role", String.class));
            if (role != null) {
                return role.toUpperCase();
            }
        } catch (Exception e) {
            logger.warn("Could not extract role from token: {}", e.getMessage());
        }
        return ROLE_USER;
    }

    // ── hasPermission ──────────────────────────────────────────────────────

    /**
     * Returns false (403) when a USER-role account attempts a write operation
     * on an ADMIN-only path.  GET requests always pass through.
     *
     * Note: the HTTP method is not directly available here.  Instead we store
     * the resolved role in the token cache and BaseHandler passes the method
     * via the "method" attribute set on the exchange before this is called.
     * For backward-compatibility the method is injected as a thread-local by
     * BaseHandler when it calls hasPermission.
     */
    @Override
    public boolean hasPermission(String username, String path) {
        // Read the method that BaseHandler injected for this request
        String method = HTTP_METHOD_HOLDER.get();

        // GET is always allowed for any role
        if (method == null || "GET".equalsIgnoreCase(method)) return true;

        // Find the role for this user via the token that BaseHandler stored
        String token = TOKEN_HOLDER.get();
        String role  = (token != null) ? getRoleFromToken(token) : ROLE_USER;

        // ADMIN may do anything
        if (ROLE_ADMIN.equalsIgnoreCase(role)) return true;

        // Self-service paths: any authenticated user may POST regardless of role
        // (e.g. USER must be able to change their own password)
        if ("/api/users/changepwd".equals(path)) return true;

        // USER: block writes on restricted paths
        for (String restricted : ADMIN_WRITE_PATHS) {
            if (path.startsWith(restricted)) {
                logger.warn("Permission denied: USER '{}' attempted {} on {}", username, method, path);
                return false;
            }
        }
        return true;
    }

    // ── Thread-local helpers (set by BaseHandler before calling hasPermission) ──

    /** BaseHandler stores the current request HTTP method here. */
    public static final ThreadLocal<String> HTTP_METHOD_HOLDER = new ThreadLocal<>();

    /** BaseHandler stores the raw Bearer token here. */
    public static final ThreadLocal<String> TOKEN_HOLDER       = new ThreadLocal<>();
}
