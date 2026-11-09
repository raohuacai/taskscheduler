package com.ts.service.impl;

import com.ts.common.constants.Constants;
import com.ts.core.transaction.TransactionManager;
import com.ts.core.transaction.TransactionManagerFactory;
import com.ts.dao.TaskInstanceDao;
import com.ts.dao.WfInstanceDao;
import com.ts.model.entity.TaskInstance;
import com.ts.model.entity.WfInstance;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the B3 fix in {@link ExecutionServiceImpl#getWfInstance}:
 * returns {@code null} (rather than throwing) when the instance does not exist,
 * so the pre-existing null-checks at every call site take effect. The parameter
 * guard (null/empty id) still throws.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionServiceImplTest {

    @Mock
    private WfInstanceDao wfInstanceDao;
    @Mock
    private TaskInstanceDao taskInstanceDao;
    @Mock
    private TransactionManager transactionManager;

    private MockedStatic<TransactionManagerFactory> factoryMock;
    private ExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        // ExecutionServiceImpl's constructor calls TransactionManagerFactory.getInstance()
        factoryMock = mockStatic(TransactionManagerFactory.class);
        factoryMock.when(TransactionManagerFactory::getInstance).thenReturn(transactionManager);
        service = new ExecutionServiceImpl(wfInstanceDao, taskInstanceDao);
    }

    @AfterEach
    void tearDown() {
        factoryMock.close();
    }

    // ==================== getWfInstance — B3 behavior ====================

    @Test
    void getWfInstance_found_returnsInstance() {
        WfInstance found = new WfInstance();
        found.setWfInstanceId("wf-inst-1");
        when(wfInstanceDao.getWfInstance("wf-inst-1")).thenReturn(found);

        WfInstance result = service.getWfInstance("wf-inst-1");

        assertNotNull(result);
        assertEquals("wf-inst-1", result.getWfInstanceId());
    }

    @Test
    void getWfInstance_notFound_returnsNull() {
        // B3 fix: previously threw IllegalArgumentException; now returns null
        when(wfInstanceDao.getWfInstance("missing-inst")).thenReturn(null);

        WfInstance result = service.getWfInstance("missing-inst");

        assertNull(result);
    }

    // ==================== getWfInstance — parameter guard (unchanged) ====================

    @Test
    void getWfInstance_nullId_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.getWfInstance(null));
    }

    @Test
    void getWfInstance_emptyId_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.getWfInstance(""));
    }

    // ==================== getWfInstance — no DAO interaction on invalid input ====================

    @Test
    void getWfInstance_invalidInput_doesNotTouchDao() {
        assertThrows(IllegalArgumentException.class, () -> service.getWfInstance(""));
        verify(wfInstanceDao, never()).getWfInstance(anyString());
    }

    // ==================== updateTaskStatusTransactional ====================

    @Test
    void updateTaskStatusTransactional_noActiveTx_running_beginsCommitsAndUpdates() {
        when(transactionManager.isTransactionActive()).thenReturn(false);
        when(transactionManager.beginTransaction()).thenReturn(mock(SqlSession.class));

        service.updateTaskStatusTransactional("task-x", Constants.STATUS_RUNNING, "");

        verify(transactionManager).beginTransaction();
        verify(taskInstanceDao).updateStatusToRunning(any(TaskInstance.class));
        verify(transactionManager).commitTransaction();
        verify(transactionManager, never()).rollbackTransaction();
    }

    @Test
    void updateTaskStatusTransactional_noActiveTx_completed_usesPlainUpdate() {
        when(transactionManager.isTransactionActive()).thenReturn(false);
        when(transactionManager.beginTransaction()).thenReturn(mock(SqlSession.class));

        service.updateTaskStatusTransactional("task-x", Constants.STATUS_COMPLETED, "done");

        verify(taskInstanceDao).updateStatus(any(TaskInstance.class));
        verify(transactionManager).commitTransaction();
    }

    @Test
    void updateTaskStatusTransactional_error_setsErrorFields() {
        when(transactionManager.isTransactionActive()).thenReturn(false);
        when(transactionManager.beginTransaction()).thenReturn(mock(SqlSession.class));

        service.updateTaskStatusTransactional("task-x", Constants.STATUS_ERROR, "boom");

        verify(taskInstanceDao).updateStatusToError(any(TaskInstance.class));
        verify(transactionManager).commitTransaction();
    }

    @Test
    void updateTaskStatusTransactional_activeTx_doesNotBeginOrCommit() {
        when(transactionManager.isTransactionActive()).thenReturn(true);

        service.updateTaskStatusTransactional("task-x", Constants.STATUS_COMPLETED, "done");

        verify(transactionManager, never()).beginTransaction();
        verify(transactionManager, never()).commitTransaction();
        verify(taskInstanceDao).updateStatus(any(TaskInstance.class));
    }

    // ==================== updateStatusTransactional (workflow) ====================

    @Test
    void updateStatusTransactional_running_updatesWfRunning() {
        when(transactionManager.isTransactionActive()).thenReturn(false);
        when(transactionManager.beginTransaction()).thenReturn(mock(SqlSession.class));

        service.updateStatusTransactional("wf-inst-x", Constants.STATUS_RUNNING, "");

        verify(wfInstanceDao).updateStatusToRunning(any(WfInstance.class));
        verify(transactionManager).commitTransaction();
    }
}
