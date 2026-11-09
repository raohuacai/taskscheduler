package com.ts.task.sql;

import com.ts.exception.TaskExecutionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RowCountCompareTask}: the strict row-count comparison
 * between a source and a target database, the handling of the different
 * {@code COUNT(*)} return types produced by Oracle / PostgreSQL / MySQL
 * drivers, and the JDBC driver resolution from the URL.
 *
 * <p>The task's protected fields are assigned directly (the test lives in the
 * same package) so {@link RowCountCompareTask#action} can be exercised against
 * mock connections without a real database.
 */
class RowCountCompareTaskTest {

    // ==================== action: comparison outcome ====================

    @Test
    void action_countsEqual_succeedsAndPersistsMatch() throws Exception {
        RowCountCompareTask task = newTask(countConnection(1234L), countConnection(1234L));
        Map<String, Object> params = new HashMap<>();

        task.action(params);

        assertEquals(1234L, params.get("sourceRowCount"));
        assertEquals(1234L, params.get("targetRowCount"));
        assertEquals(0L, params.get("rowCountDiff"));
        assertEquals(true, params.get("rowCountMatch"));
    }

    @Test
    void action_targetHasFewerRows_failsWithMismatchMessage() throws Exception {
        RowCountCompareTask task = newTask(countConnection(1234L), countConnection(1230L));
        Map<String, Object> params = new HashMap<>();

        TaskExecutionException e = assertThrows(TaskExecutionException.class, () -> task.action(params));

        assertTrue(e.getMessage().contains("source=1234"), "message was: " + e.getMessage());
        assertTrue(e.getMessage().contains("target=1230"), "message was: " + e.getMessage());
        assertTrue(e.getMessage().contains("diff=4"), "message was: " + e.getMessage());
        assertEquals(4L, params.get("rowCountDiff"));
        assertEquals(false, params.get("rowCountMatch"));
    }

    @Test
    void action_targetHasMoreRows_reportsNegativeDiff() throws Exception {
        RowCountCompareTask task = newTask(countConnection(10L), countConnection(15L));
        Map<String, Object> params = new HashMap<>();

        assertThrows(TaskExecutionException.class, () -> task.action(params));

        assertEquals(-5L, params.get("rowCountDiff"));
        assertEquals(false, params.get("rowCountMatch"));
    }

    // ==================== queryCount: driver return types ====================

    @Test
    void queryCount_decimalFromOracle_isParsed() throws Exception {
        RowCountCompareTask task = newTask(countConnection(new BigDecimal("987654321")), countConnection(0L));

        assertEquals(987654321L, task.queryCount(task.sourceConnection, "SELECT COUNT(*) FROM T", "source"));
    }

    @Test
    void queryCount_numericString_isParsed() throws Exception {
        RowCountCompareTask task = newTask(countConnection("42"), countConnection(0L));

        assertEquals(42L, task.queryCount(task.sourceConnection, "SELECT COUNT(*) FROM T", "source"));
    }

    @Test
    void queryCount_noRows_throws() throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        RowCountCompareTask task = newTask(conn, conn);

        TaskExecutionException e = assertThrows(TaskExecutionException.class,
                () -> task.queryCount(task.sourceConnection, "SELECT COUNT(*) FROM T", "source"));
        assertTrue(e.getMessage().contains("No result returned by the source"), "message was: " + e.getMessage());
    }

    @Test
    void queryCount_nullValue_throws() throws Exception {
        RowCountCompareTask task = newTask(countConnection(null), countConnection(0L));

        TaskExecutionException e = assertThrows(TaskExecutionException.class,
                () -> task.queryCount(task.sourceConnection, "SELECT COUNT(*) FROM T", "source"));
        assertTrue(e.getMessage().contains("NULL returned by the source"), "message was: " + e.getMessage());
    }

    @Test
    void queryCount_nonNumericValue_throws() throws Exception {
        RowCountCompareTask task = newTask(countConnection("not-a-number"), countConnection(0L));

        TaskExecutionException e = assertThrows(TaskExecutionException.class,
                () -> task.queryCount(task.sourceConnection, "SELECT COUNT(*) FROM T", "source"));
        assertTrue(e.getMessage().contains("Non-numeric value returned by the source"), "message was: " + e.getMessage());
    }

    // ==================== driver resolution ====================

    @Test
    void resolveDriverClass_detectsByUrlPrefix() throws Exception {
        RowCountCompareTask task = newTask(countConnection(0L), countConnection(0L));

        assertEquals("oracle.jdbc.OracleDriver",
                task.resolveDriverClass("jdbc:oracle:thin:@host:1521:SID", null));
        assertEquals("org.postgresql.Driver",
                task.resolveDriverClass("jdbc:postgresql://host:5432/db", null));
        assertEquals("com.mysql.cj.jdbc.Driver",
                task.resolveDriverClass("jdbc:mysql://host:3306/db", null));
        // Prefix matching is case-insensitive
        assertEquals("oracle.jdbc.OracleDriver",
                task.resolveDriverClass("JDBC:ORACLE:thin:@host:1521:SID", null));
    }

    @Test
    void resolveDriverClass_explicitNameWins() throws Exception {
        RowCountCompareTask task = newTask(countConnection(0L), countConnection(0L));

        assertEquals("com.example.CustomDriver",
                task.resolveDriverClass("jdbc:oracle:thin:@host:1521:SID", "com.example.CustomDriver"));
        assertEquals("com.example.CustomDriver",
                task.resolveDriverClass("jdbc:unknown://host", "  com.example.CustomDriver  "));
    }

    @Test
    void resolveDriverClass_unknownPrefix_throws() throws Exception {
        RowCountCompareTask task = newTask(countConnection(0L), countConnection(0L));

        TaskExecutionException e = assertThrows(TaskExecutionException.class,
                () -> task.resolveDriverClass("jdbc:unknown://host/db", null));
        assertTrue(e.getMessage().contains("Cannot auto-detect the JDBC driver"), "message was: " + e.getMessage());
    }

    // ==================== helpers ====================

    /** Builds a task with the two connections and SQL texts already wired up. */
    private RowCountCompareTask newTask(Connection source, Connection target) {
        RowCountCompareTask task = new RowCountCompareTask();
        task.sourceConnection = source;
        task.targetConnection = target;
        task.sourceSql = "SELECT COUNT(*) FROM SDBMGR.DATAX_SOURCE_DATA WHERE 1=1";
        task.targetSql = "SELECT COUNT(*) FROM SDBMGR.DATAX_TARGET_DATA WHERE 1=1";
        return task;
    }

    /**
     * Builds a connection whose count query returns {@code countValue} as the
     * first column of the first row, mimicking {@code SELECT COUNT(*)}.
     */
    private Connection countConnection(Object countValue) throws Exception {
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getObject(1)).thenReturn(countValue);
        return conn;
    }
}
