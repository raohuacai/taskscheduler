/*
 * Description: SQL task streaming a SELECT result set to a UTF-8 CSV file
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.sql;

import com.ts.common.constants.Constants;
import com.ts.common.utils.DateUtils;
import com.ts.common.utils.ParamUtils;
import com.ts.exception.TaskExecutionException;
import com.ts.exception.TaskTimeoutException;
import com.ts.service.impl.TaskLogService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
/**
 * SQL task that executes a SELECT query and exports the result set to a CSV file.
 *
 * <p>Writes a UTF-8 BOM header for Excel compatibility, streams rows directly
 * from the {@link java.sql.ResultSet} to the output file (no full in-memory
 * materialisation), and flushes at configurable intervals. On cancellation,
 * any partially written output file is deleted to prevent downstream consumers
 * from reading incomplete data.
 *
 * @see SqlTask
 */
public class ExportCsvTask extends SqlTask {
    private static final Logger logger = LogManager.getLogger(ExportCsvTask.class);

    // Default values
    private static final String DEFAULT_DELIMITER = ",";
    private static final boolean DEFAULT_INCLUDE_HEADER = true;
    private static final boolean DEFAULT_QUOTE_STRINGS = true;

    // Task specific properties
    private String  csvDirectory;
    private String  csvFilename;
    private String  fullFilePath;
    private String  sql;
    private String  csvDelimiter;
    private boolean includeHeader;
    private boolean quoteStrings;
    private boolean actionSucceeded = false;

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

        // Get CSV-specific parameters
        csvDirectory = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_CSV_DIRECTORY, taskId, taskInstanceId);
        csvFilename = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_CSV_FILENAME, taskName) + "-" + DateUtils.getCurrentDateTimeString() + ".csv";

        // Get optional CSV formatting parameters
        csvDelimiter = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_CSV_DELIMITER, DEFAULT_DELIMITER);
        String includeHeaderStr = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_CSV_INCLUDE_HEADER, String.valueOf(DEFAULT_INCLUDE_HEADER));
        includeHeader = Boolean.parseBoolean(includeHeaderStr);
        String quoteStringsStr = ParamUtils.getParameter(parameters, Constants.PARAM_TASK_CSV_QUOTE_STRINGS, String.valueOf(DEFAULT_QUOTE_STRINGS));
        quoteStrings = Boolean.parseBoolean(quoteStringsStr);

        // Get SQL statement using the constant from Constants class
        sql = ParamUtils.getRequiredParameter(parameters, Constants.PARAM_TASK_JDBC_SQL, taskId, taskInstanceId);

        // Ensure directory exists
        File directory = new File(csvDirectory);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + csvDirectory);
            }
        }

        // Create full file path
        fullFilePath = csvDirectory + File.separator + csvFilename;

        logger.debug("CSV export will be saved to: {}, Task: {}, {}, {}", fullFilePath, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "CSV export will be saved to: " + fullFilePath);
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
        logger.info("Starting CSV export task for query: {}, Task: {}, {}, {}", sql, taskId, taskName, taskInstanceId);

        try {
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Executing CSV export sql: " + sql);
            // Execute query and export to CSV
            int rowsExported = executeExport(sql, csvDelimiter, includeHeader, quoteStrings, parameters);

            // Check if export was interrupted by cancel.
            // executeExport() breaks out of the result-set loop when canceled.get()==true
            // and returns the partial row count normally. Without this check, action()
            // would proceed to saveParams(successResult) and the task would finish as
            // COMPLETED instead of CANCELED.
            if (canceled.get()) {
                logger.info("CSV export canceled. Task: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "CSV export canceled");
                // Throw so WorkflowInstance.executeTask() detects task.isCanceled()==true
                // and keeps the task status as CANCELED (not COMPLETED).
                throw new TaskExecutionException(
                        "CSV export canceled", null, taskId, taskInstanceId);
            }

            // Store execution results in parameters (memory + DB)
            Map<String, Object> successResult = new HashMap<>();
            successResult.put("csvExportSuccess", true);
            successResult.put("csvFilePath", fullFilePath);
            successResult.put("csvRowsExported", rowsExported);
            saveParams(parameters, successResult);
            actionSucceeded = true;

            // Log the results
            logger.info("CSV export executed successfully. Rows exported: {}, File: {}, Task: {}, {}, {}",
                    rowsExported, fullFilePath, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "CSV export executed successfully. Rows exported: " + rowsExported);

        } catch (SQLTimeoutException e) {
            // Store error information in parameters (memory + DB)
            Map<String, Object> timeoutResult = new HashMap<>();
            timeoutResult.put("csvExportSuccess", false);
            timeoutResult.put("csvErrorMessage", e.getMessage());
            timeoutResult.put("csvSqlState", e.getSQLState());
            timeoutResult.put("csvVendorCode", e.getErrorCode());
            saveParams(parameters, timeoutResult);

            logger.error("CSV export timeout: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}",
                    e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId);

            throw new TaskTimeoutException("Fail to execute CSV export: " + e.getMessage(), e, taskId, taskInstanceId);

        } catch (SQLException e) {
            // Store error information in parameters (memory + DB)
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("csvExportSuccess", false);
            errorResult.put("csvErrorMessage", e.getMessage());
            errorResult.put("csvSqlState", e.getSQLState());
            errorResult.put("csvVendorCode", e.getErrorCode());
            saveParams(parameters, errorResult);

            logger.error("CSV export failed: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}",
                    e.getMessage(), e.getSQLState(), e.getErrorCode(), taskId, taskName, taskInstanceId);

            throw new TaskExecutionException("Fail to execute CSV export: " + e.getMessage(), e, taskId, taskInstanceId);

        } catch (IOException e) {
            // Store error information in parameters (memory + DB)
            Map<String, Object> ioErrorResult = new HashMap<>();
            ioErrorResult.put("csvExportSuccess", false);
            ioErrorResult.put("csvErrorMessage", e.getMessage());
            saveParams(parameters, ioErrorResult);

            logger.error("CSV file writing failed: {}, Task: {}, {}, {}",
                    e.getMessage(), taskId, taskName, taskInstanceId);

            throw new TaskExecutionException("Fail to write CSV file: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    /**
     * Executes a SQL query and exports the results to a CSV file
     *
     * @return number of rows exported
     */
    private int executeExport(String sql, String delimiter, boolean includeHeader, boolean quoteStrings,
                              Map<String, Object> parameters) throws SQLException, IOException {

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            // Set query timeout
            stmt.setQueryTimeout(timeout);

            // Bind parameters
            bindParameters(stmt);

            // Log the SQL being executed
            logger.debug("Executing query for CSV export: {}, Task: {}, {}, {}", sql, taskId, taskName, taskInstanceId);

            // Execute the query
            try (ResultSet rs = stmt.executeQuery();
                 BufferedWriter writer = new BufferedWriter(new FileWriter(fullFilePath))) {

                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();

                // Write header if required
                if (includeHeader) {
                    StringBuilder headerLine = new StringBuilder();

                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) {
                            headerLine.append(delimiter);
                        }
                        String columnName = metaData.getColumnLabel(i);
                        if (quoteStrings) {
                            headerLine.append("\"").append(escapeQuotes(columnName)).append("\"");
                        } else {
                            headerLine.append(columnName);
                        }
                    }

                    writer.write(headerLine.toString());
                    writer.newLine();
                }

                // Write data rows
                int rowCount = 0;
                while (rs.next()) {
                    if (canceled.get()) {
                        logger.info("Task was cancelled. Stopping CSV export. Task: {}, {}, {}",
                                taskId, taskName, taskInstanceId);
                        break;
                    }

                    StringBuilder dataLine = new StringBuilder();

                    for (int i = 1; i <= columnCount; i++) {
                        if (i > 1) {
                            dataLine.append(delimiter);
                        }

                        String value = rs.getString(i);

                        if (value == null) {
                            // Leave empty for null values
                            dataLine.append("");
                        } else if (quoteStrings) {
                            dataLine.append("\"").append(escapeQuotes(value)).append("\"");
                        } else {
                            dataLine.append(value);
                        }
                    }

                    writer.write(dataLine.toString());
                    writer.newLine();
                    rowCount++;

                    // Log progress for large datasets
                    if (rowCount % 10000 == 0) {
                        logger.info("Exported {} rows so far... Task: {}, {}, {}",
                                rowCount, taskId, taskName, taskInstanceId);
                        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Exported " + rowCount + " rows");
                    }
                }

                return rowCount;
            }
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
                logger.warn("No value found for parameter at position {}. This may cause SQL errors. Task: {}, {}, {}",
                        paramIndex, taskId, taskName, taskInstanceId);
            }
        }
    }

    /**
     * Escapes double quotes in string values for CSV compatibility
     */
    private String escapeQuotes(String value) {
        return value.replace("\"", "\"\"");
    }

    @Override
    /**
     * Cleanup the resources.
     */
    public void cleanupResources() {
        // Delete the output file whenever the action did not complete successfully
        // (covers both cancellation and IO/SQL errors mid-write).
        if (!actionSucceeded && fullFilePath != null) {
            boolean deleted = new File(fullFilePath).delete();
            if (deleted) {
                logger.info("Deleted incomplete CSV file: {}", fullFilePath);
            } else {
                logger.warn("Failed to delete incomplete CSV file: {}", fullFilePath);
            }
        }
        super.cleanupResources();
    }
}
