/*
 * Description: SQL task streaming a SELECT result set to an Excel file using SXSSF streaming
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
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
/**
 * SQL task that executes a SELECT query and exports the result set to an
 * Excel (.xlsx) file using Apache POI.
 *
 * <p>Streams rows using {@link org.apache.poi.xssf.streaming.SXSSFWorkbook}
 * (100-row in-memory window) to keep heap usage constant regardless of result
 * set size. Column headers are derived from the {@link java.sql.ResultSetMetaData}.
 * On cancellation, the partial output file is deleted.
 *
 * @see SqlTask
 */
public class ExportExcelTask extends SqlTask {

    private static final Logger logger = LogManager.getLogger(ExportExcelTask.class);

    // ── Defaults ─────────────────────────────────────────────────────────────
    private static final String  DEFAULT_SHEET_NAME        = "Sheet1";
    private static final boolean DEFAULT_INCLUDE_HEADER    = true;

    /**
     * SXSSFWorkbook keeps this many rows in memory; older rows are flushed to a
     * temp file. Tune based on column width – 1000 is a safe default.
     */
    private static final int STREAMING_WINDOW_SIZE = 1000;

    /** Log progress every N rows */
    private static final int PROGRESS_LOG_INTERVAL = 10_000;

    // ── Task parameters ───────────────────────────────────────────────────────
    private String  excelDirectory;
    private String  excelFilename;
    private String  fullFilePath;
    private String  sql;
    private String  sheetName;
    private boolean includeHeader;
    private boolean actionSucceeded = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

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

        excelDirectory = ParamUtils.getRequiredParameter(
                parameters, Constants.PARAM_TASK_EXCEL_DIRECTORY, taskId, taskInstanceId);

        String baseFilename = ParamUtils.getParameter(
                parameters, Constants.PARAM_TASK_EXCEL_FILENAME, taskName);
        excelFilename = baseFilename + "-" + DateUtils.getCurrentDateTimeString() + ".xlsx";

        sheetName = ParamUtils.getParameter(
                parameters, Constants.PARAM_TASK_EXCEL_SHEET_NAME, DEFAULT_SHEET_NAME);

        String includeHeaderStr = ParamUtils.getParameter(
                parameters, Constants.PARAM_TASK_EXCEL_INCLUDE_HEADER,
                String.valueOf(DEFAULT_INCLUDE_HEADER));
        includeHeader = Boolean.parseBoolean(includeHeaderStr);


        sql = ParamUtils.getRequiredParameter(
                parameters, Constants.PARAM_TASK_JDBC_SQL, taskId, taskInstanceId);

        // Ensure output directory exists
        File directory = new File(excelDirectory);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + excelDirectory);
        }

        fullFilePath = excelDirectory + File.separator + excelFilename;

        logger.debug("Excel export will be saved to: {}, Task: {}, {}, {}",
                fullFilePath, taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                "Excel export will be saved to: " + fullFilePath);
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
        logger.info("Starting Excel export task, sql: {}, Task: {}, {}, {}",
                sql, taskId, taskName, taskInstanceId);

        try {
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "Executing Excel export sql: " + sql);

            int rowsExported = executeExport(parameters);

            // Check if export was interrupted by cancel.
            // executeExport() breaks out of the result-set loop when canceled.get()==true
            // and returns the partial row count normally. Without this check, action()
            // would proceed to saveParams(successResult) and the task would finish as
            // COMPLETED instead of CANCELED.
            if (canceled.get()) {
                logger.info("Excel export canceled. Task: {}, {}, {}", taskId, taskName, taskInstanceId);
                TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "Excel export canceled");
                // Throw so WorkflowInstance.executeTask() detects task.isCanceled()==true
                // and keeps the task status as CANCELED (not COMPLETED).
                throw new TaskExecutionException(
                        "Excel export canceled", null, taskId, taskInstanceId);
            }

            // Persist results into parameter store
            Map<String, Object> successResult = new HashMap<>();
            successResult.put("excelExportSuccess",  true);
            successResult.put("excelFilePath",        fullFilePath);
            successResult.put("excelRowsExported",    rowsExported);
            saveParams(parameters, successResult);
            actionSucceeded = true;

            logger.info("Excel export completed. Rows: {}, File: {}, Task: {}, {}, {}",
                    rowsExported, fullFilePath, taskId, taskName, taskInstanceId);
            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                    "Excel export completed. Rows exported: " + rowsExported);

        } catch (SQLTimeoutException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("excelExportSuccess",  false);
            result.put("excelErrorMessage",   e.getMessage());
            result.put("excelSqlState",       e.getSQLState());
            result.put("excelVendorCode",     e.getErrorCode());
            saveParams(parameters, result);

            logger.error("Excel export timeout: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}",
                    e.getMessage(), e.getSQLState(), e.getErrorCode(),
                    taskId, taskName, taskInstanceId);
            throw new TaskTimeoutException(
                    "Failed to execute Excel export (timeout): " + e.getMessage(), e, taskId, taskInstanceId);

        } catch (SQLException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("excelExportSuccess",  false);
            result.put("excelErrorMessage",   e.getMessage());
            result.put("excelSqlState",       e.getSQLState());
            result.put("excelVendorCode",     e.getErrorCode());
            saveParams(parameters, result);

            logger.error("Excel export SQL error: {} (SQL State: {}, Vendor Code: {}), Task: {}, {}, {}",
                    e.getMessage(), e.getSQLState(), e.getErrorCode(),
                    taskId, taskName, taskInstanceId);
            throw new TaskExecutionException(
                    "Failed to execute Excel export (SQL): " + e.getMessage(), e, taskId, taskInstanceId);

        } catch (IOException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("excelExportSuccess", false);
            result.put("excelErrorMessage",  e.getMessage());
            saveParams(parameters, result);

            logger.error("Excel file writing failed: {}, Task: {}, {}, {}",
                    e.getMessage(), taskId, taskName, taskInstanceId);
            throw new TaskExecutionException(
                    "Failed to write Excel file: " + e.getMessage(), e, taskId, taskInstanceId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core export logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Executes the SQL query and streams the result set into an .xlsx file.
     *
     * @return total data rows written (header row excluded)
     */
    private int executeExport(Map<String, Object> parameters) throws SQLException, IOException {

        // SXSSFWorkbook flushes rows to disk after STREAMING_WINDOW_SIZE rows in memory,
        // making it suitable for large result sets.
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW_SIZE)) {

            Sheet sheet = workbook.createSheet(sheetName);

            // Pre-build cell styles (reusing styles is critical for performance)
            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle dataStyle   = buildDataStyle(workbook);

            int excelRowIndex = 0;  // current row pointer in the sheet

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setQueryTimeout(timeout);
                bindParameters(stmt);

                logger.debug("Executing query for Excel export: {}, Task: {}, {}, {}",
                        sql, taskId, taskName, taskInstanceId);

                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData meta        = rs.getMetaData();
                    int               columnCount = meta.getColumnCount();

                    // ── Header row ────────────────────────────────────────────
                    if (includeHeader) {
                        Row headerRow = sheet.createRow(excelRowIndex++);
                        for (int col = 0; col < columnCount; col++) {
                            Cell cell = headerRow.createCell(col);
                            cell.setCellValue(meta.getColumnLabel(col + 1));
                            cell.setCellStyle(headerStyle);
                        }
                    }

                    // ── Data rows ─────────────────────────────────────────────
                    int dataRowCount = 0;
                    while (rs.next()) {
                        if (canceled.get()) {
                            logger.info("Task cancelled. Stopping Excel export. Task: {}, {}, {}",
                                    taskId, taskName, taskInstanceId);
                            break;
                        }

                        Row dataRow = sheet.createRow(excelRowIndex++);
                        for (int col = 0; col < columnCount; col++) {
                            Cell   cell  = dataRow.createCell(col);
                            String value = rs.getString(col + 1);
                            cell.setCellValue(value != null ? value : "");
                            cell.setCellStyle(dataStyle);
                        }

                        dataRowCount++;

                        if (dataRowCount % PROGRESS_LOG_INTERVAL == 0) {
                            logger.info("Exported {} rows so far... Task: {}, {}, {}",
                                    dataRowCount, taskId, taskName, taskInstanceId);
                            TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId,
                                    "Exported " + dataRowCount + " rows");
                        }
                    }

                    // ── Fixed column width ────────────────────────────────────
                    // SXSSFWorkbook (streaming) does not support autoSizeColumn after rows
                    // have been flushed to disk. Apply a sensible default width instead.
                    // 20 characters * 256 units = 5120 (POI width unit: 1/256th of a char).
                    for (int col = 0; col < columnCount; col++) {
                        sheet.setColumnWidth(col, 20 * 256);
                    }

                    // ── Write to disk ──────────────────────────────────────────
                    try (FileOutputStream fos = new FileOutputStream(fullFilePath)) {
                        workbook.write(fos);
                    }

                    return dataRowCount;
                }
            } finally {
                // SXSSFWorkbook creates temp files; always clean them up
                workbook.dispose();
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Parameter binding  (mirrors ExportCsvTask.bindParameters)
    // ─────────────────────────────────────────────────────────────────────────

    private void bindParameters(PreparedStatement stmt) throws SQLException {
        for (Map.Entry<Integer, ParamInfo> entry : orderedParams.entrySet()) {
            int       paramIndex = entry.getKey();
            ParamInfo paramInfo  = entry.getValue();
            if (paramInfo.getValue() != null) {
                setTypedParameter(stmt, paramIndex, paramInfo.getType(), paramInfo.getValue());
                logger.debug("Parameter {} bound – type: {}, value: {}",
                        paramIndex, paramInfo.getType(), paramInfo.getValue());
            } else {
                logger.warn("No value for parameter at position {}. May cause SQL errors. Task: {}, {}, {}",
                        paramIndex, taskId, taskName, taskInstanceId);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cell style builders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Header style: bold white text on a steel-blue background with thin borders.
     * Matches the visual language used in WorkflowExcelService.
     */
    private CellStyle buildHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.CORNFLOWER_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        applyThinBorders(style);
        return style;
    }

    /**
     * Data style: default font with thin borders and light-grey background on alternating
     * rows is not done here (POI streaming does not support row-by-row style lookup
     * easily) – caller can extend if needed.
     */
    private CellStyle buildDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        applyThinBorders(style);
        return style;
    }

    private void applyThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Delete the output file whenever the action did not complete successfully
     * (covers both cancellation and IO/SQL errors mid-write).
     */
    @Override
    public void cleanupResources() {
        if (!actionSucceeded && fullFilePath != null) {
            File file = new File(fullFilePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    logger.info("Deleted incomplete Excel file: {}", fullFilePath);
                } else {
                    logger.warn("Failed to delete incomplete Excel file: {}", fullFilePath);
                }
            }
        }
        super.cleanupResources();
    }
}
