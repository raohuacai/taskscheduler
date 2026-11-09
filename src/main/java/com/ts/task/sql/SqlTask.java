/*
 * Description: Base class for SQL tasks establishing JDBC connections and delegating to concrete executors
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.sql;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * Base class for all SQL-based task types.
 *
 * <p>Establishes a JDBC connection using the connection parameters from the
 * task parameter map ({@code dbUrl}, {@code dbUsername}, {@code dbPassword},
 * {@code dbDriver}), sets the query timeout, and delegates the actual SQL
 * execution to the concrete {@code SqlExecutor} created in {@link #preAction}.
 * The connection is closed in {@link #cleanupResources()} on every exit path.
 *
 * @see DMLSqlTask
 * @see SqlStatementTask
 * @see ProcedureTask
 * @see ExportCsvTask
 * @see ExportExcelTask
 */
public abstract class SqlTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(SqlTask.class);

    /** Matches simple positional parameter keys of the form {@code paramN} (case-insensitive),
     *  e.g. {@code param1}, {@code PARAM2}. Capture group 1 is the 1-based index. */
    protected static final Pattern PARAM_PATTERN = Pattern.compile("(?i)param(\\d+)");

    /** Matches structured parameter property keys of the form
     *  {@code paramN.property} (case-insensitive), where {@code property} is one of
     *  {@code type}, {@code value}, {@code name}, or {@code direction}.
     *  Capture group 1 is the index; group 2 is the property name. */
    protected static final Pattern PARAM_PROPERTY_PATTERN = Pattern.compile("(?i)param(\\d+)\\.(type|value|name|direction)");

    // Map to store processed parameters
    /** Positional SQL parameters parsed from the task parameter map in {@link #extractParameters};
     *  keyed by 1-based index and used in the correct order when binding a
     *  {@link java.sql.PreparedStatement} or {@link java.sql.CallableStatement}. */
    protected Map<Integer, ParamInfo> orderedParams;

    /** JDBC connection opened in {@code preAction()} and closed in
     *  {@link #cleanupResources()}; shared across all statement executions
     *  within a single task run. */
    protected Connection connection;

    /** JDBC connection URL read from the {@code dbUrl} task parameter
     *  (e.g. {@code jdbc:oracle:thin:@host:1521/SID}). */
    protected String jdbcUrl;

    /** Database username read from the {@code dbUsername} task parameter. */
    protected String username;

    /** Database password read from the {@code dbPassword} task parameter;
     *  never logged or included in error messages. */
    protected String password;

    // Reference to the SQL executor created during preAction
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

        // Retrieve database connection parameters from task parameters
        jdbcUrl = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_JDBC_URL, taskId, taskInstanceId);
        username = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_JDBC_USERNAME, taskId, taskInstanceId);
        password = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_JDBC_PASSWORD, taskId, taskInstanceId);

        // Create the SQL executor and hold a reference for lifecycle management
        timeoutExecutor = Executors.newSingleThreadExecutor();

        // Establish the database connection
        connection = DriverManager.getConnection(jdbcUrl, username, password);

        // Set the query execution timeout
        connection.setNetworkTimeout(timeoutExecutor, timeout * 1000);

        // Extract and process SQL parameters
        extractParameters(parameters);

        logger.debug("got database connection: {}", jdbcUrl);
        //TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "got database connection: " + jdbcUrl);
    }

    /**
     * Extract parameters from the parameters map
     * Handles param1.type, param1.value, param1.name, param1.direction
     */
    protected void extractParameters(Map<String, Object> parameters) {
        // Use TreeMap to ensure parameters are processed in correct order
        orderedParams = new TreeMap<>();

        // First, collect simple paramX entries (for backward compatibility)
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Matcher matcher = PARAM_PATTERN.matcher(key);

            if (matcher.matches() && !key.toLowerCase().contains(".")) {
                // Extract the parameter index
                int paramIndex = Integer.parseInt(matcher.group(1));

                // Initialize param info if not exists
                orderedParams.putIfAbsent(paramIndex, new ParamInfo());

                // Set the value directly
                orderedParams.get(paramIndex).setValue(entry.getValue());
            }
        }

        // Next, process paramX.property entries
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Matcher matcher = PARAM_PROPERTY_PATTERN.matcher(key);

            if (matcher.find()) {
                // Extract parameter index and property name
                int paramIndex = Integer.parseInt(matcher.group(1));
                String property = matcher.group(2).toLowerCase();

                // Initialize param info if not exists
                orderedParams.putIfAbsent(paramIndex, new ParamInfo());

                // Set the appropriate property
                ParamInfo paramInfo = orderedParams.get(paramIndex);
                switch (property) {
                    case "type":
                        paramInfo.setType(String.valueOf(entry.getValue()).toUpperCase());
                        break;
                    case "value":
                        // support dynamic value
                        String newValue = entry.getValue().toString();
                        //newValue = ParamUtils.getDynamicValue(newValue, parameters);
                        paramInfo.setValue(newValue);
                        break;
                    case "name":
                        paramInfo.setName(String.valueOf(entry.getValue()).toUpperCase());
                        break;
                    case "direction":
                        paramInfo.setDirection(String.valueOf(entry.getValue()).toUpperCase());
                        break;
                }
            }
        }

        // Log extracted parameters for debugging
        if (logger.isDebugEnabled()) {
            for (Map.Entry<Integer, ParamInfo> entry : orderedParams.entrySet()) {
                logger.debug("Parameter {}: {}", entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    /**
     * Post the action.
     *
     * @param parameters  parameter map
     * @throws Exception  if an error occurs
     */
    public void postAction(Map<String, Object> parameters) throws Exception {
        try {
            super.postAction(parameters);
        } finally {
            //closeResources();
        }
    }

    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        closeResources();
        super.cleanupResources();
    }

    // Resource cleanup method for the SQL executor
    private void closeResources() {
        // Shut down and release the database connection
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.debug("Closed connection: {}", connection);
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Closed connection");
                }
            } catch (SQLException e) {
                logger.error("Fail to close database connection", e);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Fail to close database connection");
            } finally {
                connection = null;
            }
        }

        // Shut down the SQL executor and release the connection
        if (timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
            timeoutExecutor = null;
            logger.debug("timeoutExecutor is shut down");
        }
    }

    /**
     * Sets a parameter with a specific type to the PreparedStatement
     */
    protected void setTypedParameter(PreparedStatement stmt, int paramIndex, String type, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(paramIndex, Constants.SQL_TYPE_NULL);
            return;
        }

        // Convert type to uppercase for case-insensitive comparison
        String upperType = type.toUpperCase();

        // Determine how to handle the parameter based on the specified type
        switch (upperType) {
            case Constants.TYPE_INTEGER:
                if (value instanceof Integer) {
                    stmt.setInt(paramIndex, (Integer) value);
                } else {
                    stmt.setInt(paramIndex, Integer.parseInt(value.toString()));
                }
                break;

            case Constants.TYPE_BIGINT:
                if (value instanceof Long) {
                    stmt.setLong(paramIndex, (Long) value);
                } else {
                    stmt.setLong(paramIndex, Long.parseLong(value.toString()));
                }
                break;

            case Constants.TYPE_DOUBLE:
            case Constants.TYPE_FLOAT:
                if (value instanceof Double) {
                    stmt.setDouble(paramIndex, (Double) value);
                } else {
                    stmt.setDouble(paramIndex, Double.parseDouble(value.toString()));
                }
                break;

            case Constants.TYPE_VARCHAR:
            case Constants.TYPE_VARCHAR2:
            case Constants.TYPE_STRING:
                stmt.setString(paramIndex, value.toString());
                break;

            case Constants.TYPE_DATE:
                if (value instanceof java.util.Date) {
                    stmt.setDate(paramIndex, new Date(((java.util.Date) value).getTime()));
                } else if (value instanceof java.time.LocalDate) {
                    stmt.setDate(paramIndex, Date.valueOf((java.time.LocalDate) value));
                } else {
                    // Try to parse as a date string
                    try {
                        stmt.setDate(paramIndex, Date.valueOf(value.toString()));
                    } catch (IllegalArgumentException e) {
                        throw new SQLException("Invalid date format: " + value);
                    }
                }
                break;

            case Constants.TYPE_TIMESTAMP:
            case Constants.TYPE_DATETIME:
                if (value instanceof java.util.Date) {
                    stmt.setTimestamp(paramIndex, new Timestamp(((java.util.Date) value).getTime()));
                } else if (value instanceof java.time.LocalDateTime) {
                    stmt.setTimestamp(paramIndex, Timestamp.valueOf((java.time.LocalDateTime) value));
                } else {
                    // Try to parse as a timestamp string
                    try {
                        stmt.setTimestamp(paramIndex, Timestamp.valueOf(value.toString()));
                    } catch (IllegalArgumentException e) {
                        throw new SQLException("Invalid timestamp format: " + value);
                    }
                }
                break;

            case Constants.TYPE_BOOLEAN:
            case Constants.TYPE_BOOL:
                if (value instanceof Boolean) {
                    stmt.setBoolean(paramIndex, (Boolean) value);
                } else {
                    stmt.setBoolean(paramIndex, Boolean.parseBoolean(value.toString()));
                }
                break;

            case Constants.TYPE_DECIMAL:
            case Constants.TYPE_NUMERIC:
                if (value instanceof java.math.BigDecimal) {
                    stmt.setBigDecimal(paramIndex, (java.math.BigDecimal) value);
                } else {
                    stmt.setBigDecimal(paramIndex, new java.math.BigDecimal(value.toString()));
                }
                break;

            case Constants.TYPE_BLOB:
            case Constants.TYPE_BINARY:
                if (value instanceof byte[]) {
                    stmt.setBytes(paramIndex, (byte[]) value);
                } else {
                    throw new SQLException("Binary parameter requires byte array value");
                }
                break;

            case Constants.TYPE_CLOB:
            case Constants.TYPE_TEXT:
                if (value instanceof Clob) {
                    stmt.setClob(paramIndex, (Clob) value);
                } else {
                    // Create a temporary CLOB
                    stmt.setString(paramIndex, value.toString());
                }
                break;

            default:
                // If type is not specified or not recognized, try to determine from value
                if (value instanceof Integer) {
                    stmt.setInt(paramIndex, (Integer) value);
                } else if (value instanceof Long) {
                    stmt.setLong(paramIndex, (Long) value);
                } else if (value instanceof Double || value instanceof Float) {
                    stmt.setDouble(paramIndex, ((Number) value).doubleValue());
                } else if (value instanceof Boolean) {
                    stmt.setBoolean(paramIndex, (Boolean) value);
                } else if (value instanceof java.util.Date) {
                    stmt.setTimestamp(paramIndex, new Timestamp(((java.util.Date) value).getTime()));
                } else if (value instanceof java.time.LocalDate) {
                    stmt.setDate(paramIndex, Date.valueOf((java.time.LocalDate) value));
                } else if (value instanceof java.time.LocalDateTime) {
                    stmt.setTimestamp(paramIndex, Timestamp.valueOf((java.time.LocalDateTime) value));
                } else if (value instanceof java.math.BigDecimal) {
                    stmt.setBigDecimal(paramIndex, (java.math.BigDecimal) value);
                } else if (value instanceof byte[]) {
                    stmt.setBytes(paramIndex, (byte[]) value);
                } else {
                    // Default to string for any other type
                    stmt.setString(paramIndex, value.toString());
                }
                break;
        }
    }

    /**
     * Helper class to store parameter information
     */
    protected static class ParamInfo {
        private String type = "";         // Parameter type (e.g. INTEGER, STRING)
        private Object value;             // Parameter value
        private String name = "";         // Parameter name (for stored procedures)
        private String direction = "IN";  // Parameter direction (IN, OUT, INOUT)

        /**
         * Returns the type.
         *
         * @return the result as a String
         */
        public String getType() {
            return type;
        }

        /**
         * Sets the type.
         *
         * @param type  type discriminator
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Returns the value.
         *
         * @return the requested value object, or null if not found
         */
        public Object getValue() {
            return value;
        }

        /**
         * Sets the value.
         *
         * @param value  value value
         */
        public void setValue(Object value) {
            this.value = value;
        }

        /**
         * Returns the name.
         *
         * @return the result as a String
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name  name name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Sets the direction.
         *
         * @param direction  file-system path
         */
        public void setDirection(String direction) {
            this.direction = direction;
        }

        /**
         * Check if this is an input parameter
         */
        public boolean isIn() {
            return "IN".equals(direction) || "INOUT".equals(direction);
        }

        /**
         * Check if this is an output parameter
         */
        public boolean isOut() {
            return "OUT".equals(direction) || "INOUT".equals(direction);
        }

        @Override
        /**
         * To the string.
         *
         * @return the result as a String
         */
        public String toString() {
            return "ParamInfo{" +
                    "type='" + type + '\'' +
                    ", value=" + value +
                    ", name='" + name + '\'' +
                    ", direction='" + direction + '\'' +
                    '}';
        }
    }
}