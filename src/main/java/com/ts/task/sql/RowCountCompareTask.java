/*
 * Description: SQL task comparing the row count of a source query against a target query on two different databases
 * Author: Rock Rao
 * Date: 9/29/2026
 * Version: 1.0
 */
package com.ts.task.sql;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/**
 * Task type that verifies an ETL load by comparing the row count of a source
 * query against the row count of a target query, executed on two independent
 * JDBC connections.
 *
 * <p>Typical use: after a {@link com.ts.task.script.DataXTask} has copied data
 * from the BY (Oracle) database into the MES database (Oracle or PostgreSQL),
 * this task runs e.g. {@code SELECT COUNT(*) FROM SDBMGR.DATAX_SOURCE_DATA} on
 * the source and {@code SELECT COUNT(*) FROM SDBMGR.DATAX_TARGET_DATA} on the
 * target. When the two counts are equal the task succeeds; otherwise it fails
 * with a {@link TaskExecutionException}, so the workflow ends in ERROR and the
 * usual notification chain fires.
 *
 * <p>Both connections are configured entirely from task parameters
 * ({@code sourceJdbcUrl} / {@code sourceUsername} / {@code sourcePassword} /
 * {@code sourceSql} and the matching {@code target*} keys). The JDBC driver is
 * either given explicitly via {@code sourceDriverClassName} / {@code
 * targetDriverClassName}, or auto-detected from the URL prefix (Oracle,
 * PostgreSQL, MySQL). Note that the matching driver jar must be on the
 * classpath - in particular PostgreSQL requires {@code postgresql-42.x.jar}
 * in {@code libx/}.
 *
 * <p>The resolved counts are persisted as task parameters
 * ({@code sourceRowCount}, {@code targetRowCount}, {@code rowCountDiff},
 * {@code rowCountMatch}) so downstream tasks can reference them via
 * {@code {tasks.<taskId>.rowCountDiff}}.
 *
 * @see SqlTask
 */
public class RowCountCompareTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(RowCountCompareTask.class);

    /** JDBC URL of the source database (e.g. the BY Oracle instance). */
    protected String sourceJdbcUrl;

    /** Source database username. */
    protected String sourceUsername;

    /** Source database password; never logged or included in error messages. */
    protected String sourcePassword;

    /** Query whose first column of the first row is taken as the source row count. */
    protected String sourceSql;

    /** Optional explicit source JDBC driver class; auto-detected from the URL when blank. */
    protected String sourceDriverClassName;

    /** JDBC URL of the target database (e.g. the MES Oracle/PostgreSQL instance). */
    protected String targetJdbcUrl;

    /** Target database username. */
    protected String targetUsername;

    /** Target database password; never logged or included in error messages. */
    protected String targetPassword;

    /** Query whose first column of the first row is taken as the target row count. */
    protected String targetSql;

    /** Optional explicit target JDBC driver class; auto-detected from the URL when blank. */
    protected String targetDriverClassName;

    /** Connection to the source database, opened in {@link #preAction} and closed in {@link #cleanupResources}. */
    protected Connection sourceConnection;

    /** Connection to the target database, opened in {@link #preAction} and closed in {@link #cleanupResources}. */
    protected Connection targetConnection;

    /** Executor used as the network-timeout handler for both connections; shut down on cleanup. */
    private ExecutorService timeoutExecutor;

    @Override
    /**
      * Prepares resources and reads task parameters before the main action.
      * Called by {@link AbstractTask#execute} before {@link #action}.
      *
      * @param parameters  merged task parameter map supplied by the workflow engine
      * @throws Exception  if a required parameter is missing or resource initialisation fails
      */
    public void preAction(Map<String, Object> parameters) throws Exception {
        super.preAction(parameters);

        // Source (e.g. BY Oracle) and target (e.g. MES Oracle/PostgreSQL) connection settings
        sourceJdbcUrl = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_SOURCE_JDBC_URL, taskId, taskInstanceId);
        sourceUsername = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_SOURCE_JDBC_USERNAME, taskId, taskInstanceId);
        sourcePassword = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_SOURCE_JDBC_PASSWORD, taskId, taskInstanceId);
        sourceSql = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_SOURCE_JDBC_SQL, taskId, taskInstanceId);
        sourceDriverClassName = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_SOURCE_DRIVER_CLASS, null);

        targetJdbcUrl = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_TARGET_JDBC_URL, taskId, taskInstanceId);
        targetUsername = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_TARGET_JDBC_USERNAME, taskId, taskInstanceId);
        targetPassword = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_TARGET_JDBC_PASSWORD, taskId, taskInstanceId);
        targetSql = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_TARGET_JDBC_SQL, taskId, taskInstanceId);
        targetDriverClassName = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_TARGET_DRIVER_CLASS, null);

        timeoutExecutor = Executors.newSingleThreadExecutor();

        // Open both connections up front so a connectivity problem fails the task before any query runs
        sourceConnection = openConnection(sourceJdbcUrl, sourceUsername, sourcePassword, sourceDriverClassName);
        targetConnection = openConnection(targetJdbcUrl, targetUsername, targetPassword, targetDriverClassName);

        // Bound network reads so a hung database cannot block the task beyond taskTimeout
        sourceConnection.setNetworkTimeout(timeoutExecutor, timeout * 1000);
        targetConnection.setNetworkTimeout(timeoutExecutor, timeout * 1000);

        logger.debug("Row count comparison connections established: source={}, target={}", sourceJdbcUrl, targetJdbcUrl);
    }

    @Override
    /**
     * Executes the core task logic.
     * Called by {@link AbstractTask#execute} after {@link #preAction}.
     *
     * @param parameters  merged task parameter map
     * @throws Exception  if the task logic fails
     */
    public void action(Map<String, Object> parameters) throws Exception {
        long sourceCount = queryCount(sourceConnection, sourceSql, "source");
        long targetCount = queryCount(targetConnection, targetSql, "target");
        long diff = sourceCount - targetCount;
        boolean match = sourceCount == targetCount;

        // Persist the outcome so downstream tasks can branch on it, e.g. {tasks.<taskId>.rowCountDiff}
        Map<String, Object> updates = new HashMap<>();
        updates.put("sourceRowCount", sourceCount);
        updates.put("targetRowCount", targetCount);
        updates.put("rowCountDiff", diff);
        updates.put("rowCountMatch", match);
        saveParams(parameters, updates);

        String summary = "Row count comparison: source=" + sourceCount + ", target=" + targetCount + ", diff=" + diff;
        logger.info("{}, match={}, Task: {}, {}, {}", summary, match, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, summary);

        if (!match) {
            throw new TaskExecutionException(
                    "Row count mismatch: source=" + sourceCount + ", target=" + targetCount + ", diff=" + diff,
                    null, taskId, taskInstanceId);
        }
    }

    /**
     * Opens a JDBC connection, resolving and loading the driver first.
     *
     * <p>Kept {@code protected} so unit tests can substitute a mock connection.
     *
     * @param url                JDBC URL of the database
     * @param user               database username
     * @param pass               database password
     * @param driverClassName    explicit driver class name, or {@code null}/blank to auto-detect from the URL
     * @return the open connection
     * @throws Exception  if the driver is unavailable or the connection cannot be established
     */
    protected Connection openConnection(String url, String user, String pass, String driverClassName) throws Exception {
        String resolvedDriver = resolveDriverClass(url, driverClassName);
        try {
            Class.forName(resolvedDriver);
        } catch (ClassNotFoundException e) {
            throw new TaskExecutionException(
                    "JDBC driver not found on classpath: " + resolvedDriver
                            + ". Add the driver jar (e.g. postgresql-42.x.jar for PostgreSQL) to libx/,"
                            + " or point driverClassName at an available driver.",
                    e, taskId, taskInstanceId);
        }
        logger.debug("JDBC driver loaded: {} for {}", resolvedDriver, url);
        return DriverManager.getConnection(url, user, pass);
    }

    /**
     * Determines the JDBC driver class to use for a connection.
     *
     * <p>An explicitly configured driver name always wins; otherwise the class is
     * inferred from the URL prefix.
     *
     * @param url                JDBC URL of the database
     * @param explicitClassName  driver class name from the task parameters, may be {@code null} or blank
     * @return the fully qualified driver class name
     * @throws TaskExecutionException  if the URL scheme is unknown and no driver name was configured
     */
    protected String resolveDriverClass(String url, String explicitClassName) throws TaskExecutionException {
        if (explicitClassName != null && !explicitClassName.trim().isEmpty()) {
            return explicitClassName.trim();
        }
        if (url == null) {
            throw new TaskExecutionException("JDBC url is required to resolve the driver class",
                    taskId, taskInstanceId);
        }
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (lowerUrl.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (lowerUrl.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        throw new TaskExecutionException(
                "Cannot auto-detect the JDBC driver for url: " + url
                        + ". Set the driverClassName parameter explicitly (e.g. org.postgresql.Driver).",
                taskId, taskInstanceId);
    }

    /**
     * Runs a count query and returns its result as a {@code long}.
     *
     * <p>Only the first column of the first row is read. Drivers return
     * {@code COUNT(*)} in different Java types - Oracle yields
     * {@link java.math.BigDecimal}, PostgreSQL and MySQL yield {@link Long} -
     * so the value is normalised through {@link Number#longValue()} and only
     * falls back to parsing when the driver hands back a string.
     *
     * <p>Kept {@code protected} so unit tests can drive it with a mock connection.
     *
     * @param connection  the connection to query
     * @param sql         the count query to execute
     * @param label       {@code source} or {@code target}, used in log and error messages
     * @return the row count reported by the query
     * @throws Exception  if the query fails, returns no row, or returns an unparseable value
     */
    protected long queryCount(Connection connection, String sql, String label) throws Exception {
        logger.info("Executing {} row count SQL: {}, Task: {}, {}, {}", label, sql, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing " + label + " sql: " + sql);

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setQueryTimeout(timeout);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new TaskExecutionException(
                            "No result returned by the " + label + " row count query: " + sql,
                            taskId, taskInstanceId);
                }
                return toRowCount(rs.getObject(1), label, sql);
            }
        } catch (SQLTimeoutException e) {
            logger.error("{} row count query timeout: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}",
                    label, e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId);
            throw new TaskTimeoutException("Execute " + label + " SQL timeout: " + e.getMessage(), e, taskId, taskInstanceId);
        } catch (SQLException e) {
            logger.error("{} row count query failed: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}",
                    label, e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId, e);
            throw new TaskExecutionException("Fail to execute " + label + " SQL: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    /**
     * Normalises the value returned by a count query into a {@code long}.
     *
     * @param value  the raw value of the first column
     * @param label  {@code source} or {@code target}, used in error messages
     * @param sql    the query that produced the value, used in error messages
     * @return the parsed row count
     * @throws TaskExecutionException  if the value is null or not numeric
     */
    private long toRowCount(Object value, String label, String sql) throws TaskExecutionException {
        if (value == null) {
            throw new TaskExecutionException(
                    "NULL returned by the " + label + " row count query: " + sql,
                    taskId, taskInstanceId);
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new TaskExecutionException(
                    "Non-numeric value returned by the " + label + " row count query: '" + value
                            + "'. The query must return a single count column: " + sql,
                    e, taskId, taskInstanceId);
        }
    }

    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        closeConnection(sourceConnection, "source");
        sourceConnection = null;
        closeConnection(targetConnection, "target");
        targetConnection = null;

        if (timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
            timeoutExecutor = null;
            logger.debug("timeoutExecutor is shut down");
        }

        super.cleanupResources();
    }

    /**
     * Closes one connection, logging (but not propagating) any failure so the
     * remaining resources are still released.
     *
     * @param connection  the connection to close, may be {@code null}
     * @param label       {@code source} or {@code target}, used in messages
     */
    private void closeConnection(Connection connection, String label) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
                logger.debug("Closed {} connection", label);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Closed " + label + " connection");
            }
        } catch (SQLException e) {
            logger.error("Fail to close {} database connection", label, e);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Fail to close " + label + " database connection");
        }
    }
}
