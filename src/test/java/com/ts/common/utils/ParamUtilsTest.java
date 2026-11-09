package com.ts.common.utils;

import com.ts.common.constants.Constants;
import com.ts.exception.TaskExecutionException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ParamUtils}: parameter reading/guards, timeout resolution,
 * lock name/owner derivation, and {variable} substitution.
 */
class ParamUtilsTest {

    private Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ==================== getParameter ====================

    @Test
    void getParameter_returnsValue() {
        assertEquals("v", ParamUtils.getParameter(params("k", "v"), "k"));
    }

    @Test
    void getParameter_missing_returnsEmptyString() {
        assertEquals("", ParamUtils.getParameter(params(), "missing"));
    }

    @Test
    void getParameter_nullMap_returnsDefault() {
        assertEquals("dflt", ParamUtils.getParameter(null, "k", "dflt"));
    }

    // ==================== numeric / boolean ====================

    @Test
    void getIntParameter_valid_parses() {
        assertEquals(60, ParamUtils.getIntParameter(params("k", "60"), "k", 0));
    }

    @Test
    void getIntParameter_invalid_fallsBackToDefault() {
        assertEquals(7, ParamUtils.getIntParameter(params("k", "abc"), "k", 7));
    }

    @Test
    void getBooleanParameter_parses() {
        assertTrue(ParamUtils.getBooleanParameter(params("k", "true"), "k", false));
        assertFalse(ParamUtils.getBooleanParameter(params("k", "garbage"), "k", false));
    }

    // ==================== getRequiredParameter ====================

    @Test
    void getRequiredParameter_present_returnsValue() {
        assertEquals("x", ParamUtils.getRequiredParameter(params("k", "x"), "k", "t1", "ti1"));
    }

    @Test
    void getRequiredParameter_missing_throws() {
        assertThrows(TaskExecutionException.class,
                () -> ParamUtils.getRequiredParameter(params(), "k", "t1", "ti1"));
    }

    @Test
    void getRequiredParameter_blank_throws() {
        assertThrows(TaskExecutionException.class,
                () -> ParamUtils.getRequiredParameter(params("k", "  "), "k", "t1", "ti1"));
    }

    // ==================== getTimeout ====================

    @Test
    void getTimeout_valid_usesValue() {
        assertEquals(120, ParamUtils.getTimeout(params(Constants.PARAM_TASK_TIMEOUT, "120"), Constants.PARAM_TASK_TIMEOUT, 3600));
    }

    @Test
    void getTimeout_invalid_fallsBackToDefault() {
        assertEquals(3600, ParamUtils.getTimeout(params(Constants.PARAM_TASK_TIMEOUT, "abc"), Constants.PARAM_TASK_TIMEOUT, 3600));
        assertEquals(3600, ParamUtils.getTimeout(params(Constants.PARAM_TASK_TIMEOUT, "0"), Constants.PARAM_TASK_TIMEOUT, 3600));
    }

    // ==================== getLockName / getLockOwner ====================

    @Test
    void getLockName_defaultUsesWorkflowNameAndId() {
        Map<String, Object> p = params(
                Constants.PARAM_WORKFLOW_NAME, "WF",
                Constants.PARAM_WORKFLOW_ID, "WF1");
        assertEquals("WF(WF1)", ParamUtils.getLockName(p));
    }

    @Test
    void getLockName_explicitOverridesDefault() {
        assertEquals("custom-lock", ParamUtils.getLockName(
                params(Constants.LOCK_NAME, "custom-lock")));
    }

    @Test
    void getLockOwner_defaultUsesWorkflowInstanceId() {
        assertEquals("inst-1", ParamUtils.getLockOwner(
                params(Constants.PARAM_WORKFLOW_INSTANCE, "inst-1")));
    }

    // ==================== variable substitution ====================

    @Test
    void replaceVariablesOnePass_substitutesKnownKeys() {
        assertEquals("aWFb", ParamUtils.replaceVariablesOnePass(
                "a{workflowName}b", params(Constants.PARAM_WORKFLOW_NAME, "WF")));
    }

    @Test
    void replaceVariablesOnePass_escapeDoubleBraces() {
        assertEquals("{raw}", ParamUtils.replaceVariablesOnePass("{{raw}}", params("raw", "NOT")));
    }

    @Test
    void replaceVariablesOnePass_dollarPlaceholderNotSubstituted() {
        assertEquals("${x}", ParamUtils.replaceVariablesOnePass("${x}", params("x", "v")));
    }

    @Test
    void replaceVariablesOnePass_unknownKeyLeftAsIs() {
        assertEquals("{unknown}", ParamUtils.replaceVariablesOnePass("{unknown}", params()));
    }

    @Test
    void replaceAllVariables_updatesMapValues() {
        Map<String, Object> p = params(
                Constants.PARAM_WORKFLOW_INSTANCE, "inst-9",
                "url", "http://x/{workflowInstanceId}/end");
        Map<String, Object> result = ParamUtils.replaceAllVariables(p);
        assertEquals("http://x/inst-9/end", result.get("url"));
    }

    // ==================== likePattern ====================

    @Test
    void likePattern_wrapsWithPercent() {
        assertEquals("%abc%", ParamUtils.likePattern("abc"));
    }

    @Test
    void likePattern_blank_returnsAsIs() {
        assertNull(ParamUtils.likePattern(null));
    }
}
