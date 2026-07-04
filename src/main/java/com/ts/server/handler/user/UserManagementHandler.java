/*
 * Description: HTTP handler for user account management (ADMIN CRUD + self-service password change)
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.server.handler.user;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.ts.server.AuditContext;
import com.ts.common.utils.PasswordUtil;
import com.ts.mapper.UserMapper;
import com.ts.model.entity.User;
import com.ts.server.handler.BaseHandler;
import com.ts.service.AuthService;
import com.ts.service.impl.AuthServiceImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
/**
 * HTTP handler for user account management ({@code /api/users/*}).
 *
 * <p>ADMIN users can create, update, delete accounts, and reset passwords.
 * Any authenticated user can change their own password via
 * {@code PUT /api/users/changepwd}. All other write operations are
 * restricted to ADMIN role.
 */
public class UserManagementHandler extends BaseHandler {
    private static final Logger logger = LogManager.getLogger(UserManagementHandler.class);
    private final UserMapper userMapper;

    /**
     * Constructs a new UserManagementHandler instance.
     *
     * @param gson  gson value
     * @param ipRestrictionEnabled  ipRestrictionEnabled value
     * @param allowedIps  allowedIps value
     * @param authService  service dependency
     * @param userMapper  userMapper value
     */
    public UserManagementHandler(Gson gson, boolean ipRestrictionEnabled,
                                 Set<String> allowedIps, AuthService authService,
                                 UserMapper userMapper) {
        super(gson, ipRestrictionEnabled, allowedIps, authService);
        this.userMapper = userMapper;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path   = exchange.getRequestURI().getPath(); // e.g. /api/users  or /api/users/resetpwd
        try {
            if ("GET".equalsIgnoreCase(method)) {
                handleList(exchange);
            } else if ("POST".equalsIgnoreCase(method)) {
                if (path.endsWith("/resetpwd")) {
                    handleResetPassword(exchange);
                } else if (path.endsWith("/changepwd")) {
                    handleChangePassword(exchange);
                } else {
                    handleCreate(exchange);
                }
            } else if ("PUT".equalsIgnoreCase(method)) {
                handleUpdate(exchange);
            } else if ("DELETE".equalsIgnoreCase(method)) {
                handleDelete(exchange);
            } else {
                sendErrorResponse(exchange, 405, "Method not allowed");
            }
        } catch (Exception e) {
            logger.error("Error in UserManagementHandler", e);
            sendErrorResponse(exchange, 500, "Server error: " + e.getMessage());
        }
    }

    // ── GET /api/users ────────────────────────────────────────────────────────
    private void handleList(HttpExchange exchange) throws IOException {
        List<User> users = userMapper.getAllUsers();
        // Strip passwords before sending to client
        List<Map<String, Object>> safeUsers = new ArrayList<>();
        for (User u : users) {
            safeUsers.add(toSafeMap(u));
        }
        sendJsonResponse(exchange, 200, safeUsers);
    }

    // ── POST /api/users ───────────────────────────────────────────────────────
    private void handleCreate(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String username = getString(req, "username");
        String password = getString(req, "password");
        String fullName = getString(req, "fullName");
        String email    = getString(req, "email");
        String phone    = getString(req, "phone");
        String status   = getString(req, "status");
        String role     = getString(req, "role");
        String operator = (String) exchange.getAttribute("username");
        // Validation
        if (username == null || username.trim().isEmpty()) {
            sendErrorResponse(exchange, 400, "Username is required"); return;
        }
        if (password == null || password.trim().isEmpty()) {
            sendErrorResponse(exchange, 400, "Password is required"); return;
        }
        if (role == null || role.trim().isEmpty()) role = AuthServiceImpl.ROLE_USER;
        if (status == null || status.trim().isEmpty()) status = "A";
        // Check duplicate
        if (userMapper.getUserByUsername(username.trim()) != null) {
            sendErrorResponse(exchange, 400, "Username already exists: " + username); return;
        }
        // Hash password
        String salt   = PasswordUtil.generateSalt();
        String hashed = PasswordUtil.hashPassword(password, salt) + ":" + salt;
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(hashed);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(status.toUpperCase());
        user.setRole(role.toUpperCase());
        user.setCreatedBy(operator);
        user.setCreationDate(new Date());
        user.setLastUpdatedBy(operator);
        user.setLastUpdateDate(new Date());
        userMapper.insertUser(user);
        Map<String, Object> response = new HashMap<>();
        response.put("status",   "success");
        response.put("username", user.getUsername());
        logger.info("User created: {} by {}", username, operator);
        sendJsonResponse(exchange, 201, response);
    }

    // ── PUT /api/users ────────────────────────────────────────────────────────
    private void handleUpdate(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String username = getString(req, "username");
        String operator = (String) exchange.getAttribute("username");
        if (username == null || username.trim().isEmpty()) {
            sendErrorResponse(exchange, 400, "Username is required"); return;
        }
        User existing = userMapper.getUserByUsername(username.trim());
        if (existing == null) {
            sendErrorResponse(exchange, 404, "User not found: " + username); return;
        }
        if (req.containsKey("fullName")) existing.setFullName(getString(req, "fullName"));
        if (req.containsKey("email"))    existing.setEmail(getString(req, "email"));
        if (req.containsKey("phone"))    existing.setPhone(getString(req, "phone"));
        if (req.containsKey("status"))   existing.setStatus(getString(req, "status").toUpperCase());
        if (req.containsKey("role"))     existing.setRole(getString(req, "role").toUpperCase());
        existing.setLastUpdatedBy(operator);
        existing.setLastUpdateDate(new Date());
        userMapper.updateUser(existing);
        Map<String, Object> response = new HashMap<>();
        response.put("status",   "success");
        response.put("username", username);
        logger.info("User updated: {} by {}", username, operator);
        sendJsonResponse(exchange, 200, response);
    }

    // ── DELETE /api/users/{username} ──────────────────────────────────────────
    private void handleDelete(HttpExchange exchange) throws IOException {
        String username = getPathParameter(exchange);
        String operator = (String) exchange.getAttribute("username");
        if (username == null || username.trim().isEmpty()) {
            sendErrorResponse(exchange, 400, "Missing username in path"); return;
        }
        // Prevent self-deletion
        if (username.equalsIgnoreCase(operator)) {
            sendErrorResponse(exchange, 400, "Cannot delete your own account"); return;
        }
        User existing = userMapper.getUserByUsername(username.trim());
        if (existing == null) {
            sendErrorResponse(exchange, 404, "User not found: " + username); return;
        }
        userMapper.deleteUser(username.trim());
        Map<String, Object> response = new HashMap<>();
        response.put("status",   "success");
        response.put("username", username);
        logger.info("User deleted: {} by {}", username, operator);
        sendJsonResponse(exchange, 200, response);
    }

    // ── POST /api/users/resetpwd  (ADMIN resets another user's password) ──────
    private void handleResetPassword(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String username    = getString(req, "username");
        String newPassword = getString(req, "newPassword");
        String operator    = (String) exchange.getAttribute("username");
        if (username == null || username.trim().isEmpty() || newPassword == null || newPassword.trim().isEmpty()) {
            sendErrorResponse(exchange, 400, "username and newPassword are required"); return;
        }
        User user = userMapper.getUserByUsername(username.trim());
        if (user == null) {
            sendErrorResponse(exchange, 404, "User not found: " + username); return;
        }
        String salt   = PasswordUtil.generateSalt();
        String hashed = PasswordUtil.hashPassword(newPassword, salt) + ":" + salt;
        user.setPassword(hashed);
        user.setLastUpdatedBy(operator);
        user.setLastUpdateDate(new Date());
        userMapper.updateUserPassword(user);
        Map<String, Object> response = new HashMap<>();
        response.put("status",   "success");
        response.put("username", username);
        logger.info("Password reset for user: {} by admin: {}", username, operator);
        sendJsonResponse(exchange, 200, response);
    }

    // ── POST /api/users/changepwd  (any user changes their own password) ──────
    private void handleChangePassword(HttpExchange exchange) throws IOException {
        String body = readRequestBody(exchange);
        Map<String, Object> req = gson.fromJson(body, Map.class);
        String operator     = (String) exchange.getAttribute("username");
        String oldPassword  = getString(req, "oldPassword");
        String newPassword  = getString(req, "newPassword");
        if (oldPassword == null || newPassword == null
                || oldPassword.trim().isEmpty() || newPassword.trim().isEmpty()) {
            String msg = "oldPassword and newPassword are required";
            AuditContext.markFailure(msg);
            sendErrorResponse(exchange, 400, msg); return;
        }
        User user = userMapper.getUserByUsername(operator);
        if (user == null) {
            String msg = "User not found";
            AuditContext.markFailure(msg);
            sendErrorResponse(exchange, 404, msg); return;
        }
        // Verify old password
        boolean valid;
        if (!user.getPassword().contains(":")) {
            valid = oldPassword.equals(user.getPassword());
        } else {
            valid = PasswordUtil.verifyPassword(oldPassword, user.getPassword());
        }
        if (!valid) {
            String msg = "Current password is incorrect";
            AuditContext.markFailure(msg);
            sendErrorResponse(exchange, 400, msg); return;
        }
        String salt   = PasswordUtil.generateSalt();
        String hashed = PasswordUtil.hashPassword(newPassword, salt) + ":" + salt;
        user.setPassword(hashed);
        user.setLastUpdatedBy(operator);
        user.setLastUpdateDate(new Date());
        userMapper.updateUserPassword(user);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        logger.info("Password changed by user: {}", operator);
        sendJsonResponse(exchange, 200, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : null;
    }

    private Map<String, Object> toSafeMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username",        u.getUsername());
        m.put("fullName",        u.getFullName());
        m.put("email",           u.getEmail());
        m.put("phone",           u.getPhone());
        m.put("status",          u.getStatus());
        m.put("role",            u.getRole());
        m.put("createdBy",       u.getCreatedBy());
        m.put("creationDate",    u.getCreationDate());
        m.put("lastUpdatedBy",   u.getLastUpdatedBy());
        m.put("lastUpdateDate",  u.getLastUpdateDate());
        return m;
    }

    @Override
    protected boolean isAllowedColumn(String column) {
        return Set.of("USERNAME", "FULL_NAME", "STATUS", "ROLE", "CREATION_DATE")
                   .contains(column.toUpperCase());
    }
}
