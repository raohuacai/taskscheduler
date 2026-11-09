package com.ts.service.impl;

import com.ts.common.constants.Constants;
import com.ts.core.dag.WorkflowDAG;
import com.ts.dao.TaskDefinitionDao;
import com.ts.dao.TaskTypeDao;
import com.ts.dao.WfDefinitionDao;
import com.ts.exception.CycleDetectedException;
import com.ts.exception.TaskNotFoundException;
import com.ts.exception.TaskTypeNotFoundException;
import com.ts.exception.TaskValidationException;
import com.ts.exception.WorkflowNotFoundException;
import com.ts.exception.WorkflowValidationException;
import com.ts.model.entity.TaskDefinition;
import com.ts.model.entity.TaskType;
import com.ts.model.entity.WfDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefinitionServiceImpl}: workflow/task CRUD guards, and the
 * definition-level DAG build with predecessor validation and cycle detection.
 */
@ExtendWith(MockitoExtension.class)
class DefinitionServiceImplTest {

    @Mock
    private WfDefinitionDao wfDefinitionDao;
    @Mock
    private TaskDefinitionDao taskDefinitionDao;
    @Mock
    private TaskTypeDao taskTypeDao;

    private DefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DefinitionServiceImpl(wfDefinitionDao, taskDefinitionDao, taskTypeDao);
    }

    // ==================== createWorkflow ====================

    @Test
    void createWorkflow_new_returnsIdAndSaves() {
        WfDefinition wf = new WfDefinition();
        wf.setWorkflowId("WF1");
        wf.setWorkflowName("WF");
        wf.setEnableFlag(Constants.FLAG_YES);
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(null);
        when(wfDefinitionDao.getWorkflowByName("WF")).thenReturn(null);

        assertEquals("WF1", service.createWorkflow(wf));
        verify(wfDefinitionDao).saveWorkflow(wf);
    }

    @Test
    void createWorkflow_duplicateId_throws() {
        WfDefinition wf = new WfDefinition();
        wf.setWorkflowId("WF1");
        wf.setWorkflowName("WF");
        wf.setEnableFlag(Constants.FLAG_YES);
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());

        assertThrows(WorkflowValidationException.class, () -> service.createWorkflow(wf));
    }

    @Test
    void createWorkflow_duplicateName_throws() {
        WfDefinition wf = new WfDefinition();
        wf.setWorkflowId("WF1");
        wf.setWorkflowName("WF");
        wf.setEnableFlag(Constants.FLAG_YES);
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(null);
        when(wfDefinitionDao.getWorkflowByName("WF")).thenReturn(new WfDefinition());

        assertThrows(WorkflowValidationException.class, () -> service.createWorkflow(wf));
    }

    // ==================== update/delete/get workflow ====================

    @Test
    void updateWorkflow_existing_saves() {
        WfDefinition wf = new WfDefinition();
        wf.setWorkflowId("WF1");
        wf.setWorkflowName("WF");
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());

        assertTrue(service.updateWorkflow(wf));
        verify(wfDefinitionDao).updateWorkflow(wf);
    }

    @Test
    void updateWorkflow_missing_throws() {
        WfDefinition wf = new WfDefinition();
        wf.setWorkflowId("WF1");
        wf.setWorkflowName("WF");
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(null);

        assertThrows(WorkflowNotFoundException.class, () -> service.updateWorkflow(wf));
    }

    @Test
    void deleteWorkflow_deletesTasksThenWorkflow() {
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());

        assertTrue(service.deleteWorkflow("WF1"));
        verify(taskDefinitionDao).deleteTasksByWorkflowId("WF1");
        verify(wfDefinitionDao).deleteWorkflow("WF1");
    }

    @Test
    void getWorkflow_missing_throws() {
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(null);
        assertThrows(WorkflowNotFoundException.class, () -> service.getWorkflow("WF1"));
    }

    // ==================== createTask ====================

    private TaskDefinition task(String id, String name, String type) {
        TaskDefinition td = new TaskDefinition();
        td.setWorkflowId("WF1");
        td.setTaskId(id);
        td.setTaskName(name);
        td.setTaskType(type);
        return td;
    }

    @Test
    void createTask_new_savesAndReturnsId() {
        TaskDefinition td = task("T1", "Task1", "SHELL");
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());   // createTask checks workflow first
        when(taskDefinitionDao.getTask("T1")).thenReturn(null);
        when(taskDefinitionDao.getTaskByName("WF1", "Task1")).thenReturn(null);
        when(taskTypeDao.getTaskTypeByName("SHELL")).thenReturn(new TaskType());

        assertEquals("T1", service.createTask("WF1", td));
        verify(taskDefinitionDao).saveTask(td);
    }

    @Test
    void createTask_unknownType_throws() {
        TaskDefinition td = task("T1", "Task1", "BAD");
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());
        when(taskTypeDao.getTaskTypeByName("BAD")).thenReturn(null);

        assertThrows(TaskTypeNotFoundException.class, () -> service.createTask("WF1", td));
    }

    @Test
    void createTask_duplicateId_throws() {
        TaskDefinition td = task("T1", "Task1", "SHELL");
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());
        when(taskTypeDao.getTaskTypeByName("SHELL")).thenReturn(new TaskType());   // param check passes
        when(taskDefinitionDao.getTask("T1")).thenReturn(new TaskDefinition());

        assertThrows(TaskValidationException.class, () -> service.createTask("WF1", td));
    }

    @Test
    void updateTask_missing_throws() {
        TaskDefinition td = task("T1", "Task1", "SHELL");
        when(taskTypeDao.getTaskTypeByName("SHELL")).thenReturn(new TaskType());   // checkTaskParams passes
        when(taskDefinitionDao.getTask("T1")).thenReturn(null);

        assertThrows(TaskNotFoundException.class, () -> service.updateTask(td));
    }

    // ==================== deleteTask ====================

    @Test
    void deleteTask_removesDeletedIdFromSiblingPredecessors() {
        TaskDefinition deleted = task("100085", "Task1", "SHELL");
        when(taskDefinitionDao.getTask("100085")).thenReturn(deleted);

        TaskDefinition sibling = task("100086", "Task2", "SHELL");
        sibling.setPredecessors("100084,100085");
        when(taskDefinitionDao.getTasksByWorkflowId("WF1")).thenReturn(List.of(sibling));

        assertTrue(service.deleteTask("100085"));

        verify(taskDefinitionDao).deleteTask("100085");
        assertEquals("100084", sibling.getPredecessors());
        verify(taskDefinitionDao).updatePredecessors(sibling);
    }

    @Test
    void deleteTask_onlyPredecessor_clearedToNull() {
        TaskDefinition deleted = task("A", "Task A", "SHELL");
        when(taskDefinitionDao.getTask("A")).thenReturn(deleted);

        TaskDefinition sibling = task("B", "Task B", "SHELL");
        sibling.setPredecessors("A");
        when(taskDefinitionDao.getTasksByWorkflowId("WF1")).thenReturn(List.of(sibling));

        service.deleteTask("A");

        assertNull(sibling.getPredecessors());
        verify(taskDefinitionDao).updatePredecessors(sibling);
    }

    @Test
    void deleteTask_unrelatedSiblings_notUpdated() {
        TaskDefinition deleted = task("A", "Task A", "SHELL");
        when(taskDefinitionDao.getTask("A")).thenReturn(deleted);

        TaskDefinition sibling = task("B", "Task B", "SHELL");
        sibling.setPredecessors("C");
        when(taskDefinitionDao.getTasksByWorkflowId("WF1")).thenReturn(List.of(sibling));

        service.deleteTask("A");

        verify(taskDefinitionDao, never()).updatePredecessors(any());
    }

    // ==================== buildWorkflowDAG ====================

    @Test
    void buildWorkflowDAG_validEdges_buildsDag() {
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());
        TaskDefinition t1 = task("A", "A", "SHELL");
        TaskDefinition t2 = task("B", "B", "SHELL");
        t2.setPredecessors("A");
        when(taskDefinitionDao.getTasksByWorkflowId("WF1")).thenReturn(List.of(t1, t2));

        WorkflowDAG dag = service.buildWorkflowDAG("WF1");

        assertEquals(2, dag.getTaskCount());
        assertEquals(1, dag.getEdgeCount());
        assertEquals(List.of("A"), dag.getDirectPredecessors("B"));
    }

    @Test
    void buildWorkflowDAG_unknownPredecessor_throws() {
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());
        TaskDefinition t1 = task("A", "A", "SHELL");
        TaskDefinition t2 = task("B", "B", "SHELL");
        t2.setPredecessors("X");   // X not defined
        when(taskDefinitionDao.getTasksByWorkflowId("WF1")).thenReturn(List.of(t1, t2));

        assertThrows(WorkflowValidationException.class, () -> service.buildWorkflowDAG("WF1"));
    }

    @Test
    void buildWorkflowDAG_cycle_throws() {
        when(wfDefinitionDao.getWorkflow("WF1")).thenReturn(new WfDefinition());
        TaskDefinition t1 = task("A", "A", "SHELL");
        TaskDefinition t2 = task("B", "B", "SHELL");
        t1.setPredecessors("B");
        t2.setPredecessors("A");
        when(taskDefinitionDao.getTasksByWorkflowId("WF1")).thenReturn(List.of(t1, t2));

        assertThrows(CycleDetectedException.class, () -> service.buildWorkflowDAG("WF1"));
    }
}
