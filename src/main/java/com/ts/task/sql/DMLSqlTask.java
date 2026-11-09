/*
 * Description: SQL task executing a DML statement and logging the affected row count
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
import java.util.HashMap;
import java.util.Map;
/**
 * SQL task that executes a DML statement (INSERT, UPDATE, DELETE) and logs
 * the affected row count.
 *
 * <p>Supports parameter binding via {@code {variableName}} placeholders in the
 * SQL text. The affected row count is written to the task log and stored in the
 * parameter map under {@code affectedRows}.
 *
 * @see SqlTask
 */
public class DMLSqlTask extends SqlTask {
    private static final Logger logger = LogManager.getLogger(DMLSqlTask.class);

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

        try {
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing DML sql: " + sql);
            // Execute the DML statement
            int rowsAffected = executeDML(sql, parameters);

            // Store execution results in parameters (memory + DB)
            Map<String, Object> successResult = new HashMap<>();
            successResult.put("dmlSuccess", true);
            successResult.put("dmlRowsAffected", rowsAffected);
            saveParams(parameters, successResult);

            // Log the results
            logger.info("DML statement executed successfully. Rows affected: {} ,Task: {}, {}, {}", rowsAffected, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "DML statement executed successfully. Rows affected: " + rowsAffected);

        } catch (SQLTimeoutException e) {
            // Store error information in parameters (memory + DB)
            Map<String, Object> timeoutResult = new HashMap<>();
            timeoutResult.put("dmlSuccess", false);
            timeoutResult.put("dmlErrorMessage", e.getMessage());
            timeoutResult.put("dmlSqlState", e.getSQLState());
            timeoutResult.put("dmlVendorCode", e.getErrorCode());
            saveParams(parameters, timeoutResult);
            logger.error("DML execution timeout: {} (SQL State: {}, Vendor Code: {}),Task: {}, {}, {}", e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId);
            throw new TaskTimeoutException("Fail to execute DML SQL: " + e.getMessage(), e, taskId, taskInstanceId);
        } catch (SQLException e) {
            // Store error information in parameters (memory + DB)
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("dmlSuccess", false);
            errorResult.put("dmlErrorMessage", e.getMessage());
            errorResult.put("dmlSqlState", e.getSQLState());
            errorResult.put("dmlVendorCode", e.getErrorCode());
            saveParams(parameters, errorResult);
            logger.error("DML execution failed: {} (SQL State: {}, Vendor Code: {}),Task: {}, {}, {}", e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId);
            throw new TaskExecutionException("Fail to execute DML SQL: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    /**
     * Executes a DML statement and returns the number of rows affected
     */
    private int executeDML(String sql, Map<String, Object> parameters) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            // Set query timeout
            stmt.setQueryTimeout(timeout);

            // Bind parameters using previously extracted parameter information
            bindParameters(stmt);

            // Log the SQL being executed
            logger.info("Executing DML statement: {}, Task: {}, {}, {}", sql, taskId, taskName, taskInstanceId);

            // Execute the statement and get the number of rows affected
            int rowsAffected = stmt.executeUpdate();

            return rowsAffected;
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
}
