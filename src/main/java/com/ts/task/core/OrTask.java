/*
 * Description: OR-gate aggregation task that completes when any one upstream dependency succeeds
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.task.core;

import com.ts.service.impl.TaskLogService;
import com.ts.task.AbstractTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
/**
 * OR-gate aggregation task that completes as soon as any one direct upstream
 * dependency reaches {@code COMPLETED} or {@code SKIPPED}.
 *
 * <p>This task has no business logic of its own — its {@link #action} method
 * returns immediately. All OR-gate semantics are implemented in
 * {@link com.ts.core.engine.WorkflowInstance#isTaskReady}, which checks the
 * special OR condition for task IDs listed in
 * {@link com.ts.core.dag.WorkflowDAG#getOrTaskIds()}.
 */
public class OrTask extends AbstractTask {
    private static final Logger logger = LogManager.getLogger(OrTask.class);

    @Override
    /**
      * Executes the core task logic.
      * Called by {@link AbstractTask#execute} after {@link #preAction}.
      *
      * @param parameters  merged task parameter map
      * @throws Exception  if the task logic fails
      */
    public void action(Map<String, Object> parameters) throws Exception {
        logger.info("OR task triggered successfully: {}, {}, {}", taskId, taskName, taskInstanceId);
        TaskLogService.addTaskLog(workflowInstanceId, taskInstanceId, "OR join condition met, task completed");
        // The OR task has no business logic of its own; it simply succeeds immediately
    }
}