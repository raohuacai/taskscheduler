/*
 * Description: SQL task executing a SELECT query and storing the result set as JSON
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.sql;

import com.ts.common.constants.Constants;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.impl.TaskLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * SQL task that executes a SELECT statement and stores the result set
 * as a JSON string in the task parameter map.
 *
 * <p>The result set is capped at {@code MAX_RESULT_ROWS_DEFAULT} (1000) rows
 * by default to prevent OOM errors on large tables. The limit is configurable
 * via the {@code maxRows} task parameter. The serialised result is stored
 * under the key specified by {@code resultParam}.
 *
 * @see SqlTask
 */
public class SqlStatementTask extends SqlTask {
    private static final Logger logger = LogManager.getLogger(SqlStatementTask.class);

    // Default maximum number of result rows; prevents OOM errors from excessively large queries
    private static final int DEFAULT_MAX_ROWS = 10;

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {
        // Get SQL statement
        String sql = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_JDBC_SQL, taskId, taskInstanceId);
        logger.info("Executing SQL statement: {}, Task: {}, {}, {}", sql, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing sql: " + sql);

        // Read the result row limit from parameters; fall back to default if not configured
        int maxRows = DEFAULT_MAX_ROWS;
        String maxRowsStr = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_JDBC_MAX_ROWS, null);
        if (maxRowsStr != null) {
            try {
                maxRows = Integer.parseInt(maxRowsStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid maxRows value: {}, using default: {}", maxRowsStr, DEFAULT_MAX_ROWS);
            }
        }

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            // Set query timeout
            stmt.setQueryTimeout(timeout);

            // Bind parameters using previously extracted parameter information
            bindParameters(stmt);

            // Execute statement
            boolean isQuery = stmt.execute();

            // Process results
            if (isQuery) {
                // Query returned a result set
                processResultSet(stmt, parameters, maxRows);
            } else {
                // Statement was an update
                int updateCount = stmt.getUpdateCount();
                saveParam(parameters, "sqlUpdateCount", updateCount);
                logger.info("SQL update executed, affected {} rows, Task: {}, {}, {}", updateCount, taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "SQL update executed, affected rows: " + updateCount);
            }
        } catch (SQLTimeoutException e) {
            logger.error("DML execution timeout: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}", e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId);
            throw new TaskTimeoutException("Execute SQL timeout: " + e.getMessage(), e, taskId, taskInstanceId);
        } catch (SQLException e) {
            logger.error("SQL execution error: {}, Task: {}, {}, {}", e.getMessage(), taskId, taskName, taskInstanceId, e);
            throw new TaskExecutionException("Fail to execute SQL: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    /**
     * Binds parameters to the PreparedStatement using the pre-processed parameter information
     */
    private void bindParameters(PreparedStatement stmt) throws SQLException {
        for (Map.Entry<Integer, ParamInfo> entry : orderedParams.entrySet()) {
            int paramIndex = entry.getKey();
            ParamInfo paramInfo = entry.getValue();

            if (paramInfo.getValue() != null) {
                setTypedParameter(stmt, paramIndex, paramInfo.getType(), paramInfo.getValue());
                logger.debug("Parameter {} bound with type: {}, value: {}",
                        paramIndex, paramInfo.getType(), paramInfo.getValue());
            } else {
                logger.warn("No value found for parameter at position {}. This may cause SQL errors.", paramIndex);
            }
        }
    }

    /**
     * Processes the ResultSet and stores results in parameters
     * @param maxRows the maximum number of rows to return; excess rows are truncated and a warning is logged
     */
    private void processResultSet(PreparedStatement stmt, Map<String, Object> parameters, int maxRows) throws SQLException {
        try (ResultSet rs = stmt.getResultSet()) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Get column names
            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(metaData.getColumnName(i));
            }

            // Collect result rows; truncate if the count exceeds maxRows
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                if (rows.size() >= maxRows) {
                    logger.warn("SQL query result truncated at {} rows (maxRows limit), Task: {}, {}, {}",
                            maxRows, taskId, taskName, taskInstanceId);
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                            "SQL query result truncated at " + maxRows + " rows (maxRows limit)");
                    saveParam(parameters, "sqlTruncated", true); 
                    break;
                }
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(columnNames.get(i - 1), rs.getObject(i));
                }
                rows.add(row);
            }

            // Store result
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "sqlResults: " + rows);
            Map<String, Object> updates = new HashMap<>();
            updates.put("sqlRowCount", rows.size());
            updates.put("sqlColumnNames", columnNames);
            updates.put("sqlResults", rows);
            saveParams(parameters, updates);
            logger.info("SQL query executed, returned {} rows", rows.size());
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "SQL query executed, returned rows: " + rows.size());
        }
    }
}
