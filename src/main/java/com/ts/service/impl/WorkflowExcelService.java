/*
 * Description: Excel import/export service for workflow definitions using Apache POI
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ts.common.SeqId.SeqIdGenerator;
import com.ts.model.entity.TaskDefinition;
import com.ts.model.entity.TaskType;
import com.ts.model.entity.WfDefinition;
import com.ts.service.DefinitionService;
import com.ts.service.TaskTypeService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
/**
 * Handles Excel import and export of workflow definitions.
 *
 * <p>Import: parses a structured Excel workbook (Apache POI) containing workflow
 * metadata, task definitions, dependency edges, and parameter tables, then
 * persists them via the DAO layer. Validates DAG acyclicity before committing.
 *
 * <p>Export: queries existing workflow definitions and serialises them into the
 * same structured Excel format, suitable for re-import or offline editing.
 *
 * @see com.ts.server.handler.definition.WorkflowExcelHandler
 */
public class WorkflowExcelService {

    private static final Logger logger = LogManager.getLogger(WorkflowExcelService.class);

    // ── column length limits (adjust to match DDL) ────────────────────────
    private static final int MAX_WF_NAME   = 100;
    private static final int MAX_WF_DESC   = 500;
    private static final int MAX_TASK_NAME = 100;
    private static final int MAX_TASK_DESC = 500;
    private static final int MAX_TASK_TYPE = 30;
    private static final int MAX_PVALUE    = 4000;

    // ── sheet / header names ──────────────────────────────────────────────
    private static final String SHEET_WORKFLOWS    = "Workflows";
    private static final String SHEET_TASKS        = "Tasks";
    private static final String SHEET_DEPENDENCIES = "Dependencies";

    private static final String[] WF_HEADERS   = {"workflow_name", "workflow_desc", "enable_flag", "parameters"};
    private static final String[] TASK_HEADERS  = {"workflow_name", "task_name", "task_desc", "task_type", "enable_flag", "parameters"};
    private static final String[] DEP_HEADERS   = {"workflow_name", "task_name", "predecessor_task_name"};

    private final DefinitionService definitionService;
    private final TaskTypeService   taskTypeService;
    private final ObjectMapper      objectMapper = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * Constructs a new WorkflowExcelService instance.
     *
     * @param definitionService  service dependency
     * @param taskTypeService  type discriminator
     */
    public WorkflowExcelService(DefinitionService definitionService, TaskTypeService taskTypeService) {
        this.definitionService = definitionService;
        this.taskTypeService   = taskTypeService;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EXPORT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Exports the to excel.
     *
     * @param filterWorkflowId  filterWorkflowId identifier
     * @param filterWorkflowName  filterWorkflowName name
     * @return the result of the operation
     * @throws IOException  if an error occurs
     */
    public byte[] exportToExcel(String filterWorkflowId, String filterWorkflowName) throws IOException {
        com.github.pagehelper.PageInfo<WfDefinition> page =
                definitionService.search(filterWorkflowId, filterWorkflowName, 1, Integer.MAX_VALUE, "WORKFLOW_NAME ASC");
        List<WfDefinition> workflows = page.getList();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            CellStyle headerStyle  = createHeaderStyle(wb);
            CellStyle normalStyle  = createNormalStyle(wb);
            CellStyle jsonStyle    = createJsonStyle(wb);
            CellStyle altStyle     = createAltStyle(wb);
            CellStyle jsonAltStyle = createJsonAltStyle(wb);

            // ── Sheet 1: Workflows ────────────────────────────────────────
            XSSFSheet wfSheet = wb.createSheet(SHEET_WORKFLOWS);
            addInstructionRow(wfSheet, wb,
                    "Workflows – 'workflow_name' is the unique key for import matching (trimmed). " +
                    "enable_flag: Y=active, N=inactive. parameters must be valid JSON.", WF_HEADERS.length);
            writeHeaders(wfSheet, WF_HEADERS, headerStyle, 1);
            wfSheet.createFreezePane(0, 2);

            int wfRow = 2;
            for (WfDefinition wf : workflows) {
                Row row = wfSheet.createRow(wfRow);
                boolean alt = (wfRow % 2 == 0);
                setCell(row, 0, wf.getWorkflowName(),                         alt ? altStyle  : normalStyle);
                setCell(row, 1, nvl(wf.getWorkflowDesc()),                    alt ? altStyle  : normalStyle);
                setCell(row, 2, wf.getEnableFlag(),                           alt ? altStyle  : normalStyle);
                setCell(row, 3, nvl(wf.getPvalue(), "{}"),  alt ? jsonAltStyle : jsonStyle);
                wfRow++;
            }
            autoSizeColumns(wfSheet, WF_HEADERS.length, 3, 60);

            // ── Sheet 2: Tasks ────────────────────────────────────────────
            XSSFSheet taskSheet = wb.createSheet(SHEET_TASKS);
            addInstructionRow(taskSheet, wb,
                    "Tasks – matched by (workflow_name + task_name). " +
                    "task_type must match an existing system task type. parameters must be valid JSON.", TASK_HEADERS.length);
            writeHeaders(taskSheet, TASK_HEADERS, headerStyle, 1);
            taskSheet.createFreezePane(0, 2);

            int taskRow = 2;
            for (WfDefinition wf : workflows) {
                List<TaskDefinition> tasks = definitionService.getTasksByWorkflowId(wf.getWorkflowId());
                if (tasks == null) continue;
                for (TaskDefinition t : tasks) {
                    Row row = taskSheet.createRow(taskRow);
                    boolean alt = (taskRow % 2 == 0);
                    setCell(row, 0, wf.getWorkflowName(),                      alt ? altStyle  : normalStyle);
                    setCell(row, 1, t.getTaskName(),                           alt ? altStyle  : normalStyle);
                    setCell(row, 2, nvl(t.getTaskDesc()),                      alt ? altStyle  : normalStyle);
                    setCell(row, 3, nvl(t.getTaskType()),                      alt ? altStyle  : normalStyle);
                    setCell(row, 4, t.getEnableFlag(),                         alt ? altStyle  : normalStyle);
                    setCell(row, 5, nvl(t.getPvalue(), "{}"), alt ? jsonAltStyle : jsonStyle);
                    taskRow++;
                }
            }
            autoSizeColumns(taskSheet, TASK_HEADERS.length, 5, 60);

            // ── Sheet 3: Dependencies ─────────────────────────────────────
            XSSFSheet depSheet = wb.createSheet(SHEET_DEPENDENCIES);
            addInstructionRow(depSheet, wb,
                    "Dependencies – one row per dependency edge. " +
                    "Both task_name and predecessor_task_name must exist in the Tasks sheet " +
                    "(or in the database) for the same workflow.", DEP_HEADERS.length);
            writeHeaders(depSheet, DEP_HEADERS, headerStyle, 1);
            depSheet.createFreezePane(0, 2);

            int depRow = 2;
            for (WfDefinition wf : workflows) {
                List<TaskDefinition> tasks = definitionService.getTasksByWorkflowId(wf.getWorkflowId());
                if (tasks == null) continue;
                // Build taskId → taskName lookup
                Map<String, String> id2Name = new HashMap<>();
                for (TaskDefinition t : tasks) id2Name.put(t.getTaskId(), t.getTaskName());

                for (TaskDefinition t : tasks) {
                    String preds = t.getPredecessors();
                    if (preds == null || preds.trim().isEmpty()) continue;
                    for (String predId : preds.split(",")) {
                        predId = predId.trim();
                        if (predId.isEmpty()) continue;
                        String predName = id2Name.getOrDefault(predId, predId); // fallback to ID if unresolvable
                        Row row = depSheet.createRow(depRow);
                        boolean alt = (depRow % 2 == 0);
                        setCell(row, 0, wf.getWorkflowName(), alt ? altStyle : normalStyle);
                        setCell(row, 1, t.getTaskName(),      alt ? altStyle : normalStyle);
                        setCell(row, 2, predName,             alt ? altStyle : normalStyle);
                        depRow++;
                    }
                }
            }
            autoSizeColumns(depSheet, DEP_HEADERS.length, -1, 40);

            wb.write(baos);
            return baos.toByteArray();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // IMPORT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Imports the from excel.
     *
     * @param excelBytes  excelBytes value
     * @param operator  operator value
     * @return the result of the operation
     */
    public ImportResult importFromExcel(byte[] excelBytes, String operator) {
        ImportResult result = new ImportResult();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {

            // Load valid task types once (empty set = service-level check only)
            Set<String> validTaskTypes = loadValidTaskTypes();

            // ── Phase 1: read & validate ──────────────────────────────────
            List<WfDefinition>   wfRows   = new ArrayList<>();
            List<TaskDefinition> taskRows  = new ArrayList<>();
            List<String[]>       depRows   = new ArrayList<>();

            readWorkflowSheet(wb, wfRows, result);
            readTaskSheet(wb, taskRows, validTaskTypes, result);
            readDepSheet(wb, depRows, result);

            // Abort if there are validation errors and nothing was read successfully
            if (result.hasErrors() && wfRows.isEmpty() && taskRows.isEmpty()) {
                return result;
            }

            // Cross-validate dep references
            validateDependencies(depRows, taskRows, result);

            // ── Phase 2: persist workflows ────────────────────────────────
            Map<String, String> wfNameToId = new LinkedHashMap<>(); // name → workflowId

            for (WfDefinition wf : wfRows) {
                try {
                    WfDefinition existing = findWorkflowByName(wf.getWorkflowName());
                    if (existing == null) {
                        wf.setWorkflowId(SeqIdGenerator.generateWfId());
                        wf.setCreatedBy(operator);
                        wf.setCreationDate(new Date());
                        wf.setLastUpdatedBy(operator);
                        wf.setLastUpdateDate(new Date());
                        definitionService.createWorkflow(wf);
                        wfNameToId.put(wf.getWorkflowName(), wf.getWorkflowId());
                        result.incrementWorkflowsCreated();
                        logger.info("Excel import – CREATE workflow: {}", wf.getWorkflowName());
                    } else {
                        existing.setWorkflowDesc(wf.getWorkflowDesc());
                        existing.setEnableFlag(wf.getEnableFlag());
                        existing.setPvalue(wf.getPvalue());
                        existing.setLastUpdatedBy(operator);
                        existing.setLastUpdateDate(new Date());
                        definitionService.updateWorkflow(existing);
                        wfNameToId.put(wf.getWorkflowName(), existing.getWorkflowId());
                        result.incrementWorkflowsUpdated();
                        logger.info("Excel import – UPDATE workflow: {}", wf.getWorkflowName());
                    }
                } catch (Exception e) {
                    result.addError("Workflow '" + wf.getWorkflowName() + "': save error – " + e.getMessage());
                    logger.error("Import error – workflow: {}", wf.getWorkflowName(), e);
                }
            }

            // ── Phase 3: persist tasks ────────────────────────────────────
            // taskKey = wfId|taskName → taskId
            Map<String, String> taskKey2Id = new LinkedHashMap<>();

            for (TaskDefinition td : taskRows) {
                String wfName = td.getWorkflowId(); // temporarily stores workflow name
                String wfId   = resolveWorkflowId(wfName, wfNameToId, result);
                if (wfId == null) continue;

                try {
                    TaskDefinition existing = findTaskByName(wfId, td.getTaskName());
                    if (existing == null) {
                        td.setTaskId(SeqIdGenerator.generateTaskId());
                        td.setWorkflowId(wfId);
                        td.setCreatedBy(operator);
                        td.setCreationDate(new Date());
                        td.setLastUpdatedBy(operator);
                        td.setLastUpdateDate(new Date());
                        td.setPredecessors(""); // set in phase 4
                        definitionService.createTask(wfId, td);
                        taskKey2Id.put(wfId + "|" + td.getTaskName(), td.getTaskId());
                        result.incrementTasksCreated();
                        logger.info("Excel import – CREATE task: {} / {}", wfName, td.getTaskName());
                    } else {
                        existing.setTaskDesc(td.getTaskDesc());
                        existing.setTaskType(td.getTaskType());
                        existing.setEnableFlag(td.getEnableFlag());
                        existing.setPvalue(td.getPvalue());
                        existing.setLastUpdatedBy(operator);
                        existing.setLastUpdateDate(new Date());
                        definitionService.updateTask(existing);
                        taskKey2Id.put(wfId + "|" + td.getTaskName(), existing.getTaskId());
                        result.incrementTasksUpdated();
                        logger.info("Excel import – UPDATE task: {} / {}", wfName, td.getTaskName());
                    }
                } catch (Exception e) {
                    result.addError("Task '" + td.getTaskName() + "' in workflow '" + wfName + "': save error – " + e.getMessage());
                    logger.error("Import error – task: {}/{}", wfName, td.getTaskName(), e);
                }
            }

            // ── Phase 4: update dependencies ──────────────────────────────
            // Accumulate predecessor IDs per taskId (multiple dep rows per task)
            Map<String, List<String>> taskDeps = new LinkedHashMap<>();

            for (String[] dep : depRows) {
                String wfName   = dep[0];
                String taskName = dep[1];
                String predName = dep[2];

                String wfId = resolveWorkflowId(wfName, wfNameToId, null); // silent
                if (wfId == null) continue;

                String taskId = resolveTaskId(wfId, taskName, taskKey2Id);
                String predId = resolveTaskId(wfId, predName, taskKey2Id);
                if (taskId == null) {
                    result.addError("Dependencies: cannot resolve task '" + taskName + "' in workflow '" + wfName + "' to an ID – skipped.");
                    continue;
                }
                if (predId == null) {
                    result.addError("Dependencies: cannot resolve predecessor '" + predName + "' in workflow '" + wfName + "' to an ID – skipped.");
                    continue;
                }

                taskDeps.computeIfAbsent(taskId, k -> new ArrayList<>()).add(predId);
            }

            for (Map.Entry<String, List<String>> entry : taskDeps.entrySet()) {
                try {
                    TaskDefinition td = new TaskDefinition();
                    td.setTaskId(entry.getKey());
                    td.setPredecessors(String.join(",", entry.getValue()));
                    definitionService.updatePredecessors(td);
                    logger.info("Excel import – UPDATE task predecessors: {} -> {}", td.getPredecessors() ,td.getTaskId());
                } catch (Exception e) {
                    result.addError("Failed to update dependencies for task " + entry.getKey() + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            result.addError("Cannot read Excel file: " + e.getMessage());
            logger.error("Error reading uploaded Excel file", e);
        }

        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Sheet readers
    // ═════════════════════════════════════════════════════════════════════════

    private void readWorkflowSheet(XSSFWorkbook wb, List<WfDefinition> out, ImportResult result) {
        Sheet sheet = wb.getSheet(SHEET_WORKFLOWS);
        if (sheet == null) {
            result.addError("Missing sheet: '" + SHEET_WORKFLOWS + "'");
            return;
        }
        // row 0 = instructions, row 1 = headers, data starts row 2
        for (int ri = 2; ri <= sheet.getLastRowNum(); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null || isRowEmpty(row, WF_HEADERS.length)) continue;
            int excelRow = ri + 1;

            String wfName    = getCellStr(row, 0);
            String wfDesc    = getCellStr(row, 1);
            String flagRaw   = getCellStr(row, 2);
            String pvalueRaw = getCellStr(row, 3);

            boolean ok = true;
            if (wfName.isEmpty()) {
                result.addError("Workflows row " + excelRow + ": workflow_name is required"); ok = false;
            } else if (wfName.length() > MAX_WF_NAME) {
                result.addError("Workflows row " + excelRow + ": workflow_name exceeds " + MAX_WF_NAME + " chars"); ok = false;
            }
            if (wfDesc.length() > MAX_WF_DESC) {
                result.addError("Workflows row " + excelRow + ": workflow_desc exceeds " + MAX_WF_DESC + " chars"); ok = false;
            }
            String pvalue = normaliseJson(pvalueRaw, result, "Workflows row " + excelRow + " [parameters]");
            if (pvalue == null) ok = false;
            else if (pvalue.length() > MAX_PVALUE) {
                result.addError("Workflows row " + excelRow + ": parameters exceeds " + MAX_PVALUE + " chars"); ok = false;
            }
            if (!ok) continue;

            WfDefinition wf = new WfDefinition();
            wf.setWorkflowName(wfName);
            wf.setWorkflowDesc(wfDesc.isEmpty() ? null : wfDesc);
            wf.setEnableFlag(normaliseFlag(flagRaw));
            wf.setPvalue(pvalue);
            out.add(wf);
        }
    }

    private void readTaskSheet(XSSFWorkbook wb, List<TaskDefinition> out,
                               Set<String> validTaskTypes, ImportResult result) {
        Sheet sheet = wb.getSheet(SHEET_TASKS);
        if (sheet == null) {
            result.addError("Missing sheet: '" + SHEET_TASKS + "'");
            return;
        }
        for (int ri = 2; ri <= sheet.getLastRowNum(); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null || isRowEmpty(row, TASK_HEADERS.length)) continue;
            int excelRow = ri + 1;

            String wfName    = getCellStr(row, 0);
            String taskName  = getCellStr(row, 1);
            String taskDesc  = getCellStr(row, 2);
            String taskType  = getCellStr(row, 3);
            String flagRaw   = getCellStr(row, 4);
            String pvalueRaw = getCellStr(row, 5);

            boolean ok = true;
            if (wfName.isEmpty()) {
                result.addError("Tasks row " + excelRow + ": workflow_name is required"); ok = false;
            }
            if (taskName.isEmpty()) {
                result.addError("Tasks row " + excelRow + ": task_name is required"); ok = false;
            } else if (taskName.length() > MAX_TASK_NAME) {
                result.addError("Tasks row " + excelRow + ": task_name exceeds " + MAX_TASK_NAME + " chars"); ok = false;
            }
            if (taskDesc.length() > MAX_TASK_DESC) {
                result.addError("Tasks row " + excelRow + ": task_desc exceeds " + MAX_TASK_DESC + " chars"); ok = false;
            }
            if (taskType.isEmpty()) {
                result.addError("Tasks row " + excelRow + ": task_type is required"); ok = false;
            } else if (taskType.length() > MAX_TASK_TYPE) {
                result.addError("Tasks row " + excelRow + ": task_type exceeds " + MAX_TASK_TYPE + " chars"); ok = false;
            } else if (!validTaskTypes.isEmpty() && !validTaskTypes.contains(taskType)) {
                result.addError("Tasks row " + excelRow + ": unknown task_type '" + taskType +
                        "'. Valid types: " + validTaskTypes); ok = false;
            }
            String pvalue = normaliseJson(pvalueRaw, result, "Tasks row " + excelRow + " [parameters]");
            if (pvalue == null) ok = false;
            else if (pvalue.length() > MAX_PVALUE) {
                result.addError("Tasks row " + excelRow + ": parameters exceeds " + MAX_PVALUE + " chars"); ok = false;
            }
            if (!ok) continue;

            TaskDefinition td = new TaskDefinition();
            td.setWorkflowId(wfName);   // stores name temporarily; resolved to ID in phase 3
            td.setTaskName(taskName);
            td.setTaskDesc(taskDesc.isEmpty() ? null : taskDesc);
            td.setTaskType(taskType);
            td.setEnableFlag(normaliseFlag(flagRaw));
            td.setPvalue(pvalue);
            out.add(td);
        }
    }

    private void readDepSheet(XSSFWorkbook wb, List<String[]> out, ImportResult result) {
        Sheet sheet = wb.getSheet(SHEET_DEPENDENCIES);
        if (sheet == null) return; // optional
        for (int ri = 2; ri <= sheet.getLastRowNum(); ri++) {
            Row row = sheet.getRow(ri);
            if (row == null || isRowEmpty(row, DEP_HEADERS.length)) continue;
            int excelRow = ri + 1;

            String wfName   = getCellStr(row, 0);
            String taskName = getCellStr(row, 1);
            String predName = getCellStr(row, 2);

            if (wfName.isEmpty() || taskName.isEmpty() || predName.isEmpty()) {
                result.addError("Dependencies row " + excelRow + ": all three columns are required"); continue;
            }
            if (taskName.equals(predName)) {
                result.addError("Dependencies row " + excelRow + ": task_name and predecessor_task_name cannot be the same"); continue;
            }
            out.add(new String[]{wfName, taskName, predName});
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-validation
    // ─────────────────────────────────────────────────────────────────────────

    private void validateDependencies(List<String[]> depRows, List<TaskDefinition> taskRows, ImportResult result) {
        Set<String> importKeys = new HashSet<>();
        for (TaskDefinition td : taskRows) importKeys.add(td.getWorkflowId() + "|" + td.getTaskName());

        for (String[] dep : depRows) {
            String wfName = dep[0], taskName = dep[1], predName = dep[2];
            if (!importKeys.contains(wfName + "|" + taskName)     && !taskExistsInDb(wfName, taskName)) {
                result.addError("Dependencies: task '" + taskName + "' in workflow '" + wfName + "' not found.");
            }
            if (!importKeys.contains(wfName + "|" + predName)     && !taskExistsInDb(wfName, predName)) {
                result.addError("Dependencies: predecessor '" + predName + "' in workflow '" + wfName + "' not found.");
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Lookup / resolution helpers
    // ═════════════════════════════════════════════════════════════════════════

    private String resolveWorkflowId(String wfName, Map<String, String> cache, ImportResult result) {
        String wfId = cache.get(wfName);
        if (wfId != null) return wfId;
        WfDefinition dbWf = findWorkflowByName(wfName);
        if (dbWf != null) { cache.put(wfName, dbWf.getWorkflowId()); return dbWf.getWorkflowId(); }
        if (result != null) result.addError("Workflow '" + wfName + "' not found (not in import file or database).");
        return null;
    }

    private String resolveTaskId(String wfId, String taskName, Map<String, String> cache) {
        String key = wfId + "|" + taskName;
        String id  = cache.get(key);
        if (id != null) return id;
        TaskDefinition dbTask = findTaskByName(wfId, taskName);
        if (dbTask != null) { cache.put(key, dbTask.getTaskId()); return dbTask.getTaskId(); }
        return null;
    }

    private WfDefinition findWorkflowByName(String name) {
        try {
            com.github.pagehelper.PageInfo<WfDefinition> page =
                    definitionService.search(null, name, 1, 50, null);
            if (page.getList() != null) {
                for (WfDefinition wf : page.getList()) {
                    if (wf.getWorkflowName().equals(name)) return wf;
                }
            }
        } catch (Exception e) {
            logger.warn("findWorkflowByName error: {}", e.getMessage());
        }
        return null;
    }

    private TaskDefinition findTaskByName(String workflowId, String taskName) {
        try {
            List<TaskDefinition> tasks = definitionService.getTasksByWorkflowId(workflowId);
            if (tasks != null) {
                for (TaskDefinition t : tasks) {
                    if (t.getTaskName().equals(taskName)) return t;
                }
            }
        } catch (Exception e) {
            logger.warn("findTaskByName error: {}", e.getMessage());
        }
        return null;
    }

    private boolean taskExistsInDb(String wfName, String taskName) {
        WfDefinition wf = findWorkflowByName(wfName);
        if (wf == null) return false;
        return findTaskByName(wf.getWorkflowId(), taskName) != null;
    }

    private Set<String> loadValidTaskTypes() {
        Set<String> types = new LinkedHashSet<>();
        try {
            com.github.pagehelper.PageInfo<TaskType> page =
                    taskTypeService.search(null, null, 1, Integer.MAX_VALUE, "TYPE_NAME ASC");
            if (page.getList() != null) {
                for (TaskType tt : page.getList()) types.add(tt.getTypeName());
            }
        } catch (Exception e) {
            logger.warn("Could not load task types for validation: {}", e.getMessage());
        }
        return types;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Value helpers
    // ═════════════════════════════════════════════════════════════════════════

    /** N / F / n / f / 0 → "N";  anything else → "Y" */
    private String normaliseFlag(String raw) {
        if (raw == null) return "Y";
        String v = raw.trim();
        return (v.equalsIgnoreCase("N") || v.equalsIgnoreCase("F") || v.equals("0")) ? "N" : "Y";
    }

    /**
     * Validate JSON.  Blank → "{}".  Invalid → records error, returns null.
     */
    private String normaliseJson(String raw, ImportResult result, String field) {
        if (raw == null || raw.trim().isEmpty()) return "{}";
        String trimmed = raw.trim();
        try {
            objectMapper.readTree(trimmed);
            return trimmed;
        } catch (JsonProcessingException e) {
            result.addError(field + ": invalid JSON – " + e.getOriginalMessage());
            return null;
        }
    }

    private String nvl(String s)              { return s != null ? s : ""; }
    private String nvl(String s, String def)  { return (s != null && !s.isEmpty()) ? s : def; }

    // ═════════════════════════════════════════════════════════════════════════
    // POI helpers
    // ═════════════════════════════════════════════════════════════════════════

    private String getCellStr(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        cell.setCellType(CellType.STRING);
        String v = cell.getStringCellValue();
        return v == null ? "" : v.trim();
    }

    private boolean isRowEmpty(Row row, int colCount) {
        for (int c = 0; c < colCount; c++) if (!getCellStr(row, c).isEmpty()) return false;
        return true;
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private void writeHeaders(Sheet sheet, String[] headers, CellStyle style, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20);
        for (int i = 0; i < headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(style);
        }
    }

    private void addInstructionRow(Sheet sheet, XSSFWorkbook wb, String text, int colSpan) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(36);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        CellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = wb.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        cell.setCellStyle(style);
        if (colSpan > 1) sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colSpan - 1));
    }

    private void autoSizeColumns(Sheet sheet, int colCount, int jsonCol, int jsonMaxChars) {
        for (int c = 0; c < colCount; c++) {
            sheet.autoSizeColumn(c);
            if (c == jsonCol) {
                int maxW = jsonMaxChars * 256;
                int minW = 30 * 256;
                int w = sheet.getColumnWidth(c);
                sheet.setColumnWidth(c, Math.min(maxW, Math.max(w, minW)));
            }
        }
    }

    // ── cell styles ──────────────────────────────────────────────────────────

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.MEDIUM);
        Font f = wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 11); s.setFont(f);
        return s;
    }

    private CellStyle createNormalStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.HAIR); s.setBorderRight(BorderStyle.HAIR);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        return s;
    }

    private CellStyle createJsonStyle(XSSFWorkbook wb) {
        CellStyle s = createNormalStyle(wb);
        s.setWrapText(true);
        Font f = wb.createFont(); f.setFontName("Courier New"); f.setFontHeightInPoints((short) 9);
        s.setFont(f);
        return s;
    }

    private CellStyle createAltStyle(XSSFWorkbook wb) {
        CellStyle s = createNormalStyle(wb);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    private CellStyle createJsonAltStyle(XSSFWorkbook wb) {
        CellStyle s = createJsonStyle(wb);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return s;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // ImportResult
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Accumulates the outcome of a single Excel import operation.
     *
     * <p>Tracks counts of successfully imported and skipped records across all
     * sheets (workflows, tasks, parameters, edges). Also collects human-readable
     * warning messages for rows that were skipped due to validation failures,
     * so callers can surface them in the API response.
     */
    public static class ImportResult {
        private int workflowsCreated;
        private int workflowsUpdated;
        private int tasksCreated;
        private int tasksUpdated;
        private final List<String> errors = new ArrayList<>();

        /** Increments the count of workflow definitions successfully created during this import. */
        public void incrementWorkflowsCreated() { workflowsCreated++; }

        /** Increments the count of workflow definitions successfully updated during this import. */
        public void incrementWorkflowsUpdated() { workflowsUpdated++; }

        /** Increments the count of task definitions successfully created during this import. */
        public void incrementTasksCreated()     { tasksCreated++;     }

        /** Increments the count of task definitions successfully updated during this import. */
        public void incrementTasksUpdated()     { tasksUpdated++;     }

        /**
         * Appends a human-readable warning or error message to the error list.
         * Messages are included in the import API response so callers can identify
         * which rows were skipped and why.
         *
         * @param msg  the error or warning message describing the skipped row
         */
        public void addError(String msg)        { errors.add(msg);    }

        public int          getWorkflowsCreated() { return workflowsCreated; }
        public int          getWorkflowsUpdated() { return workflowsUpdated; }
        public int          getTasksCreated()     { return tasksCreated;     }
        public int          getTasksUpdated()     { return tasksUpdated;     }
        public List<String> getErrors()           { return errors;           }
        public boolean      hasErrors()           { return !errors.isEmpty();}
        public int          getTotalProcessed()   { return workflowsCreated + workflowsUpdated + tasksCreated + tasksUpdated; }
    }
}
