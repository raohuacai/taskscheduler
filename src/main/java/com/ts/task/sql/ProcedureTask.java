/*
 * Description: SQL task calling a stored procedure via JDBC CallableStatement with IN/OUT parameter support
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
 * SQL task that calls a stored procedure via JDBC {@link java.sql.CallableStatement}.
 *
 * <p>Supports IN, OUT, and INOUT parameters declared as a JSON array in the
 * {@code procedureParams} task parameter. OUT parameter values are stored in the
 * task parameter map after execution, and any result sets returned by the procedure
 * are serialised to JSON and stored under {@code resultParam}.
 *
 * @see SqlTask
 */
public class ProcedureTask extends SqlTask {
    private static final Logger logger = LogManager.getLogger(ProcedureTask.class);

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {
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
                throw new TaskExecutionException("Fail to execute procedure: " + e.getMessage(), e, taskId, taskInstanceId);
            }
        }
    }

    /**
     * Builds the JDBC CALL statement string for invoking a stored procedure
     *
     * @param procedureName the name of the stored procedure to call
     * @param paramCount    the number of parameters the procedure expects
     * @return the CALL statement string (e.g. "{call proc_name(?,?)}" )
     */
    protected String buildCallStatement(String procedureName, int paramCount) {
        StringBuilder callStmt = new StringBuilder();
        callStmt.append("{call ").append(procedureName).append("(");

        if (paramCount > 0) {
            for (int i = 0; i < paramCount; i++) {
                if (i > 0) {
                    callStmt.append(", ");
                }
                callStmt.append("?");
            }
        }

        callStmt.append(")}");
        return callStmt.toString();
    }

    /**
     * Registers output parameters and binds input parameter values on the CallableStatement
     *
     * @param stmt the CallableStatement to configure
     * @throws SQLException if a database access error occurs
     */
    protected void registerAndSetParameters(CallableStatement stmt) throws SQLException {
        for (Map.Entry<Integer, ParamInfo> entry : orderedParams.entrySet()) {
            int paramIndex = entry.getKey();
            ParamInfo paramInfo = entry.getValue();

            // Retrieve the SQL type for the output parameter
            int sqlType = getSqlType(paramInfo.getType());

            // Register the OUT parameter with the statement
            if (paramInfo.isOut()) {
                stmt.registerOutParameter(paramIndex, sqlType);
                logger.debug("Parameter {} registered as OUT parameter with type: {}",
                        paramIndex, paramInfo.getType());
            }

            // Bind input parameter values
            if (paramInfo.isIn() && paramInfo.getValue() != null) {
                setTypedParameter(stmt, paramIndex, paramInfo.getType(), paramInfo.getValue());
                logger.debug("Parameter {} set as IN parameter with type: {}, value: {}",
                        paramIndex, paramInfo.getType(), paramInfo.getValue());
            }
        }
    }

    /**
     * Extracts all output parameter values from the executed CallableStatement
     *
     * @param stmt the CallableStatement to configure
     * @return a map of output parameter name (or paramN) to its value
     * @throws SQLException if a database access error occurs
     */
    protected Map<String, Object> extractOutputParameters(CallableStatement stmt) throws SQLException {
        Map<String, Object> outParams = new HashMap<>();

        for (Map.Entry<Integer, ParamInfo> entry : orderedParams.entrySet()) {
            ParamInfo paramInfo = entry.getValue();
            int paramIndex = entry.getKey();

            if (paramInfo.isOut()) {
                int sqlType = getSqlType(paramInfo.getType());
                Object value = getOutputParameterValue(stmt, paramIndex, sqlType);

                // Use the parameter name as the key; fall back to 'paramN' if the name is empty
                String paramName = !paramInfo.getName().isEmpty() ?
                        paramInfo.getName() : "param" + paramIndex;

                outParams.put(paramName, value);
                logger.debug("OUT parameter {} retrieved with value: {}", paramName, value);
            }
        }

        return outParams;
    }

    /**
     * Retrieves the value of a single output parameter from the CallableStatement
     *
     * @param stmt       the CallableStatement
     * @param paramIndex the 1-based parameter index
     * @param sqlType    the JDBC SQL type code for the parameter
     * @return the output parameter value, or {@code null} if not applicable
     * @throws SQLException if a database access error occurs
     */
    protected Object getOutputParameterValue(CallableStatement stmt, int paramIndex, int sqlType) throws SQLException {
        // Retrieve the appropriate Java type based on the SQL type code
        try {
            switch (sqlType) {
                case Constants.SQL_TYPE_INTEGER:
                    return stmt.getInt(paramIndex);
                case Constants.SQL_TYPE_BIGINT:
                    return stmt.getLong(paramIndex);
                case Constants.SQL_TYPE_DOUBLE:
                    return stmt.getDouble(paramIndex);
                case Constants.SQL_TYPE_DECIMAL:
                case Constants.SQL_TYPE_NUMERIC:
                    return stmt.getBigDecimal(paramIndex);
                case Constants.SQL_TYPE_DATE:
                    return stmt.getDate(paramIndex);
                case Constants.SQL_TYPE_TIMESTAMP:
                    return stmt.getTimestamp(paramIndex);
                case Constants.SQL_TYPE_BOOLEAN:
                    return stmt.getBoolean(paramIndex);
                case Constants.SQL_TYPE_BLOB:
                    return stmt.getBytes(paramIndex);
                case Constants.SQL_TYPE_CLOB:
                    Clob clob = stmt.getClob(paramIndex);
                    return (clob != null) ? clob.getSubString(1, (int) clob.length()) : null;
                case Constants.SQL_TYPE_VARCHAR:
                default:
                    return stmt.getString(paramIndex);
            }
        } catch (SQLException e) {
            logger.warn("Fail to get output parameter value：{}, rollback to getObject", paramIndex, e);
            return stmt.getObject(paramIndex);
        }
    }

    /**
     * Processes a single ResultSet returned by the stored procedure
     *
     * @param rs the ResultSet to process
     * @return a list of maps, each representing one result row (column name → value)
     * @throws SQLException if a database access error occurs
     */
    protected List<Map<String, Object>> processResultSet(ResultSet rs) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        // Retrieve column names from the ResultSet metadata
        List<String> columnNames = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(metaData.getColumnName(i));
        }

        // Process each data row from the ResultSet
        while (rs.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(columnNames.get(i - 1), rs.getObject(i));
            }
            rows.add(row);

            // Periodically check whether the task has been cancelled
            if (rows.size() % 1000 == 0) {
                // Check whether the task has been cancelled or timed out
                if (canceled.get()) {
                    logger.error("Impossible, Task was canceled while processing the result set: {}, {}, {}", taskId, taskName, taskInstanceId);
                    return null;
                }
            }
        }

        return rows;
    }

    /**
     * Resolves a JDBC SQL type constant from a type name string
     *
     * @param typeName the SQL type name (e.g. "VARCHAR", "INTEGER")
     * @return the corresponding {@link java.sql.Types} constant value
     */
    protected int getSqlType(String typeName) {
        if (typeName == null || typeName.isEmpty()) return Constants.SQL_TYPE_VARCHAR;

        switch (typeName.toUpperCase()) {
            case Constants.TYPE_INTEGER:
                return Constants.SQL_TYPE_INTEGER;

            case Constants.TYPE_BIGINT:
                return Constants.SQL_TYPE_BIGINT;

            case Constants.TYPE_DECIMAL:
            case Constants.TYPE_NUMERIC:
                return Constants.SQL_TYPE_DECIMAL;

            case Constants.TYPE_FLOAT:
            case Constants.TYPE_DOUBLE:
                return Constants.SQL_TYPE_DOUBLE;

            case Constants.TYPE_DATE:
                return Constants.SQL_TYPE_DATE;

            case Constants.TYPE_DATETIME:
            case Constants.TYPE_TIMESTAMP:
                return Constants.SQL_TYPE_TIMESTAMP;

            case Constants.TYPE_BOOL:
            case Constants.TYPE_BOOLEAN:
                return Constants.SQL_TYPE_BOOLEAN;

            case Constants.TYPE_BINARY:
            case Constants.TYPE_BLOB:
                return Constants.SQL_TYPE_BLOB;

            case Constants.TYPE_TEXT:
            case Constants.TYPE_CLOB:
                return Constants.SQL_TYPE_CLOB;

            case Constants.TYPE_VARCHAR:
            case Constants.TYPE_VARCHAR2:
            case Constants.TYPE_STRING:
            default:
                return Constants.SQL_TYPE_VARCHAR;
        }
    }
}