/*
 * Description: Oracle stored procedure task with DBMS_OUTPUT capture
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
import oracle.jdbc.OracleCallableStatement;
import oracle.jdbc.OracleTypes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.Map;
/**
 * Oracle-specific stored procedure task that additionally captures output
 * written via {@code DBMS_OUTPUT.PUT_LINE}.
 *
 * <p>Enables {@code DBMS_OUTPUT} with a 1 MB buffer before executing the
 * procedure, then reads lines from the output buffer and writes them to the
 * task log. All other behaviour (IN/OUT params, result sets) is inherited
 * from {@link ProcedureTask}.
 *
 * @see ProcedureTask
 */
public class OracleProcedureTask extends ProcedureTask {
    private static final Logger logger = LogManager.getLogger(OracleProcedureTask.class);

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {
        // Enable DBMS_OUTPUT so that PUT_LINE output can be captured
        enableDbmsOutput();

        // Retrieve the stored procedure name from task parameters
        String procedureName = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_JDBC_PROCEDURENAME, taskId, taskInstanceId);

        // Build the CALL statement for the stored procedure
        String callStatement = buildCallStatement(procedureName, orderedParams.size());

        // Execute stored procedure
        try (CallableStatement stmt = connection.prepareCall(callStatement)) {
            // Set the query execution timeout
            stmt.setQueryTimeout(timeout);

            // Register output parameters and bind input parameters
            registerAndSetParameters(stmt);

            // Execute the stored procedure
            logger.info("Executing procedure: {}, Task: {}, {}, {}", procedureName, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing procedure: " + procedureName);
            boolean hasResultSet = stmt.execute();

            // Process output parameters and persist results (in memory and to DB)
            Map<String, Object> outParams = extractOutputParameters(stmt);
            saveParam(parameters, Constants.PARAM_TASK_OUTPUT, outParams);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "procedureOutParams: " + outParams.toString());

            // Retrieve DBMS_OUTPUT lines (write to log line by line; do not persist to DB)
            getDbmsOutput(parameters);

            // Check exit code if checkExitCode exists in out parameters
            if(parameters.containsKey(Constants.PARAM_TASK_JDBC_CHECKEXITCODE)) {
                String exitCode = outParams.getOrDefault(((String)parameters.get(Constants.PARAM_TASK_JDBC_CHECKEXITCODE)).toUpperCase(),"0").toString();
                if(!exitCode.trim().equals("0")){
                    throw new TaskExecutionException("Fail to execute procedure("+procedureName+"): The exit code is not 0", taskId, taskInstanceId);
                }else{
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "exitCode: " + exitCode);
                }
            }

            // Process any result sets returned by the procedure
            if (hasResultSet) {
                //processResultSets(stmt, parameters);
            } else {
                // Retrieve and persist the update count (in-memory and DB)
                int updateCount = stmt.getUpdateCount();
                if (updateCount >= 0) {
                    saveParam(parameters, "updateCount", updateCount);
                    TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "updateCount: " + updateCount);
                }
            }

            logger.info("Procedure executed successfully: {}, Task: {}, {}, {}", procedureName, taskId, taskName, taskInstanceId);
        } catch (SQLTimeoutException e) {
            logger.error("DML execution timeout: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}", e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId);
            throw new TaskTimeoutException("Execute procedure timeout: " + e.getMessage(), e, taskId, taskInstanceId);
        } catch (SQLException e) {
            if (e.getMessage().contains("Socket read timed out")) {
                throw new TaskTimeoutException("Execute procedure timeout: " + e.getMessage(), e, taskId, taskInstanceId);
            } else {
                getDbmsOutput(parameters);
                throw new TaskExecutionException("Fail to execute procedure: " + e.getMessage(), e, taskId, taskInstanceId);
            }
        }
    }

    private void enableDbmsOutput() throws SQLException {
        // Enable DBMS_OUTPUT and set the buffer size to 1 MB
        try (CallableStatement stmt = connection.prepareCall(
                "BEGIN DBMS_OUTPUT.ENABLE(1000000); END;")) {
            stmt.execute();
        }
    }

    // More efficient alternative using OracleCallableStatement directly
    private void getDbmsOutput(Map<String, Object> parameters) throws SQLException {
        StringBuilder sb = new StringBuilder();

        try (OracleCallableStatement stmt = (OracleCallableStatement) connection.prepareCall(
                "BEGIN DBMS_OUTPUT.GET_LINES(?, ?); END;")) {

            stmt.registerOutParameter(1, OracleTypes.ARRAY, "DBMSOUTPUT_LINESARRAY");
            stmt.registerOutParameter(2, OracleTypes.INTEGER);
            stmt.execute();

            String[] lines = (String[]) stmt.getArray(1).getArray();
            int numLines = stmt.getInt(2);

            for (int i = 0; i < numLines; i++) {
                sb.append(lines[i]).append("\n");
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "[DB_OUTPUT] " + lines[i]);
            }
        }
        //if (sb.toString() != null && sb.toString().length() > 0) {
        if (sb.length() > 0) {
            //parameters.put(Constants.PARAM_TASK_OUTPUT, sb.toString());
        }
    }

}