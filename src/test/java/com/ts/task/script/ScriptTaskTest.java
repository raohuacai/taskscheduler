package com.ts.task.script;

import com.ts.common.constants.Constants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the E1 fix in {@link ScriptTask#preAction}: the default working
 * directory used the misspelled system property {@code timeout.io.tmpdir}
 * (always null), so any script task without an explicit {@code working_dir}
 * failed with a NullPointerException. The fix uses {@code java.io.tmpdir}.
 *
 * <p>Uses an anonymous {@code ScriptTask} subclass implementing the two abstract
 * methods; {@code createScriptFile} is a no-op so preAction runs without side effects.
 */
class ScriptTaskTest {

    private ScriptTask newScriptTask() {
        return new ScriptTask() {
            @Override
            protected void createScriptFile() throws Exception {
                // no-op: avoid writing a real temp script file
            }

            @Override
            protected List<String> buildCommand() {
                return List.of();
            }
        };
    }

    private Map<String, Object> params(String workingDir) {
        Map<String, Object> p = new HashMap<>();
        p.put(Constants.PARAM_TASK_SCRIPT, "echo hello");
        p.put(Constants.PARAM_TASK_NAME, "TestTask");
        p.put(Constants.PARAM_TASK_INSTANCE, "task-inst-1");
        if (workingDir != null) {
            p.put(Constants.PARAM_TASK_WORKINGDIR, workingDir);
        }
        return p;
    }

    private String readWorkingDir(ScriptTask task) throws Exception {
        Field f = ScriptTask.class.getDeclaredField("workingDir");
        f.setAccessible(true);
        return (String) f.get(task);
    }

    // ==================== E1: default working dir ====================

    @Test
    void preAction_noWorkingDir_defaultsToJavaTmpDir() throws Exception {
        ScriptTask task = newScriptTask();

        task.preAction(params(null));

        String expected = System.getProperty("java.io.tmpdir");
        assertNotNull(expected, "java.io.tmpdir must be a defined system property");
        assertEquals(expected, readWorkingDir(task));
    }

    // ==================== explicit working dir still honoured ====================

    @Test
    void preAction_explicitWorkingDir_usesProvidedValue() throws Exception {
        ScriptTask task = newScriptTask();

        task.preAction(params(System.getProperty("java.io.tmpdir")));

        assertEquals(System.getProperty("java.io.tmpdir"), readWorkingDir(task));
    }
}
