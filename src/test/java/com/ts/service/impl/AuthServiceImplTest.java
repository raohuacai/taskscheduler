package com.ts.service.impl;

import com.ts.common.utils.JwtUtil;
import com.ts.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AuthServiceImpl#hasPermission}: global params are readable
 * by ADMIN only, while every other read stays open to USER and all write
 * restrictions keep working as before.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String GLOBAL_PARAMS = "/api/params/global";

    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper);
    }

    @AfterEach
    void tearDown() {
        // Thread-locals are shared per-thread; leaving them set would leak into
        // the next test on this thread.
        AuthServiceImpl.HTTP_METHOD_HOLDER.remove();
        AuthServiceImpl.TOKEN_HOLDER.remove();
    }

    /** Builds a JWT carrying the given role claim. */
    private static String tokenFor(String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return JwtUtil.generateToken("tester", claims);
    }

    private void request(String method, String role) {
        AuthServiceImpl.HTTP_METHOD_HOLDER.set(method);
        AuthServiceImpl.TOKEN_HOLDER.set(tokenFor(role));
    }

    // ==================== global params read restriction ====================

    @Test
    void userGetGlobalParams_denied() {
        request("GET", AuthServiceImpl.ROLE_USER);

        assertFalse(authService.hasPermission("tester", GLOBAL_PARAMS));
    }

    @Test
    void userGetGlobalParamsSubPath_denied() {
        // The HttpServer context also matches sub-paths, so startsWith must apply
        request("GET", AuthServiceImpl.ROLE_USER);

        assertFalse(authService.hasPermission("tester", GLOBAL_PARAMS + "/anything"));
    }

    @Test
    void adminGetGlobalParams_allowed() {
        request("GET", AuthServiceImpl.ROLE_ADMIN);

        assertTrue(authService.hasPermission("tester", GLOBAL_PARAMS));
    }

    // ==================== no over-blocking ====================

    @Test
    void userGetOtherPath_allowed() {
        request("GET", AuthServiceImpl.ROLE_USER);

        assertTrue(authService.hasPermission("tester", "/api/workflow/info/wf-1"));
        assertTrue(authService.hasPermission("tester", "/api/params/task"));
        assertTrue(authService.hasPermission("tester", "/api/params/workflow"));
    }

    @Test
    void missingMethodOnOtherPath_allowed() {
        // method null means "not a write" - treated as a read
        assertTrue(authService.hasPermission("tester", "/api/workflow/info/wf-1"));
    }

    @Test
    void nullToken_getGlobalParams_deniedWithoutNpe() {
        AuthServiceImpl.HTTP_METHOD_HOLDER.set("GET");
        AuthServiceImpl.TOKEN_HOLDER.remove();

        // must not throw NPE from the token cache lookup
        assertFalse(authService.hasPermission("tester", GLOBAL_PARAMS));
    }

    // ==================== write restrictions unchanged ====================

    @Test
    void userPutGlobalParams_denied() {
        request("PUT", AuthServiceImpl.ROLE_USER);

        assertFalse(authService.hasPermission("tester", GLOBAL_PARAMS));
    }

    @Test
    void userPostChangepwd_allowed() {
        request("POST", AuthServiceImpl.ROLE_USER);

        assertTrue(authService.hasPermission("tester", "/api/users/changepwd"));
    }

    @Test
    void adminPutGlobalParams_allowed() {
        request("PUT", AuthServiceImpl.ROLE_ADMIN);

        assertTrue(authService.hasPermission("tester", GLOBAL_PARAMS));
    }
}
