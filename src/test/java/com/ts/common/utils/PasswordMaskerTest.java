package com.ts.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PasswordMasker}: keys containing "password" are masked with
 * a same-length run of asterisks, everything else is left alone.
 */
class PasswordMaskerTest {

    // ==================== JSON object masking ====================

    @Test
    void maskJson_passwordKey_masksWithSameLength() {
        String in = "{\"jdbcUrl\":\"jdbc:mysql://localhost:3306/db\",\"username\":\"SDBMGR\",\"password\":\"SDBMGR\"}";

        String out = PasswordMasker.maskJson(in);

        assertTrue(out.contains("\"password\":\"******\""), out);
        // non-secret keys untouched
        assertTrue(out.contains("\"username\":\"SDBMGR\""), out);
        assertTrue(out.contains("\"jdbcUrl\":\"jdbc:mysql://localhost:3306/db\""), out);
    }

    @Test
    void maskJson_sourceAndTargetPassword_masks() {
        String in = "{\"sourcePassword\":\"abc\",\"targetPassword\":\"defgh\"}";

        String out = PasswordMasker.maskJson(in);

        assertTrue(out.contains("\"sourcePassword\":\"***\""), out);
        assertTrue(out.contains("\"targetPassword\":\"*****\""), out);
    }

    @Test
    void maskJson_sshPasswordKey_masks() {
        String in = "{\"ssh.host\":\"192.168.1.100\",\"ssh.user\":\"appuser\",\"ssh.password\":\"secret123\"}";

        String out = PasswordMasker.maskJson(in);

        assertTrue(out.contains("\"ssh.password\":\"*********\""), out);
        assertTrue(out.contains("\"ssh.user\":\"appuser\""), out);
    }

    @Test
    void maskJson_httpAuthPassword_masks() {
        String in = "{\"httpAuthUser\":\"user\",\"httpAuthPassword\":\"user\"}";

        String out = PasswordMasker.maskJson(in);

        assertTrue(out.contains("\"httpAuthPassword\":\"****\""), out);
        assertTrue(out.contains("\"httpAuthUser\":\"user\""), out);
    }

    @Test
    void maskJson_lengthIsPreserved() {
        String in = "{\"password\":\"Abcd1234\",\"other\":\"value\"}";

        String out = PasswordMasker.maskJson(in);

        assertEquals(in.length(), out.length());
    }

    @Test
    void maskJson_emptyPasswordValue_producesEmptyValue() {
        String out = PasswordMasker.maskJson("{\"password\":\"\"}");

        assertEquals("{\"password\":\"\"}", out);
    }

    // ==================== recursion ====================

    @Test
    void maskJson_nestedObject_masks() {
        String in = "{\"db\":{\"username\":\"u\",\"password\":\"pwd\"}}";

        String out = PasswordMasker.maskJson(in);

        assertTrue(out.contains("\"password\":\"***\""), out);
        assertTrue(out.contains("\"username\":\"u\""), out);
    }

    @Test
    void maskJson_arrayValue_masks() {
        String in = "{\"servers\":[{\"password\":\"ab\"},{\"password\":\"cde\"}]}";

        String out = PasswordMasker.maskJson(in);

        assertTrue(out.contains("\"password\":\"**\""), out);
        assertTrue(out.contains("\"password\":\"***\""), out);
    }

    @Test
    void maskJson_nonStringSecretValue_isLeftAsIs() {
        String out = PasswordMasker.maskJson("{\"password\":123,\"passwordFlag\":true}");

        // numbers/booleans carry no secret; only string values are masked
        assertEquals("{\"password\":123,\"passwordFlag\":true}", out);
    }

    // ==================== non-secret keys ====================

    @Test
    void maskJson_nonSecretKeys_unchanged() {
        String in = "{\"username\":\"SDBMGR\",\"jdbcUrl\":\"jdbc:mysql://h/db\",\"privateKeyPassphrase\":\"kp\"}";

        assertEquals(in, PasswordMasker.maskJson(in));
    }

    @Test
    void maskJson_specialChars_areNotHtmlEscaped() {
        // '&', '=' and '<' are common in jdbcUrl / SQL values; re-serialising
        // must not turn them into unicode escape sequences.
        String in = "{\"jdbcUrl\":\"jdbc:mysql://h/db?useSSL=false&allowPublicKeyRetrieval=true\","
                  + "\"sql\":\"select 1 where a < 5\"}";

        assertEquals(in, PasswordMasker.maskJson(in));
    }

    // ==================== degenerate input ====================

    @Test
    void maskJson_nullBlankAndInvalidJson_returnedAsIs() {
        assertNull(PasswordMasker.maskJson(null));
        assertEquals("", PasswordMasker.maskJson(""));
        assertEquals("   ", PasswordMasker.maskJson("   "));
        assertEquals("not json at all", PasswordMasker.maskJson("not json at all"));
    }

    // ==================== isSecretKey ====================

    @Test
    void isSecretKey_caseInsensitiveContains() {
        assertTrue(PasswordMasker.isSecretKey("password"));
        assertTrue(PasswordMasker.isSecretKey("sourcePassword"));
        assertTrue(PasswordMasker.isSecretKey("TARGETPASSWORD"));
        assertTrue(PasswordMasker.isSecretKey("HttpAuthPassword"));
        assertTrue(PasswordMasker.isSecretKey("ssh.password"));

        assertFalse(PasswordMasker.isSecretKey("username"));
        assertFalse(PasswordMasker.isSecretKey("privateKeyPassphrase"));
        assertFalse(PasswordMasker.isSecretKey(null));
    }

    // ==================== stars ====================

    @Test
    void stars_zeroAndNegative_returnEmptyString() {
        assertEquals("", PasswordMasker.stars(0));
        assertEquals("", PasswordMasker.stars(-3));
        assertEquals("*", PasswordMasker.stars(1));
        assertEquals("******", PasswordMasker.stars(6));
    }
}
