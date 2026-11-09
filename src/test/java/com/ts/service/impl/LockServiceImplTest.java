package com.ts.service.impl;

import com.ts.dao.TSLockDao;
import com.ts.model.entity.TSLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link LockServiceImpl}: acquire/release and bulk release semantics.
 */
@ExtendWith(MockitoExtension.class)
class LockServiceImplTest {

    @Mock
    private TSLockDao tsLockDao;

    private LockServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LockServiceImpl(tsLockDao);
    }

    // ==================== acquireLock ====================

    @Test
    void acquireLock_success_returnsTrue() {
        when(tsLockDao.acquireLock(any(TSLock.class))).thenReturn(true);

        assertTrue(service.acquireLock("lock1", "owner1"));
        verify(tsLockDao).acquireLock(any(TSLock.class));
    }

    @Test
    void acquireLock_heldByOther_returnsFalse() {
        when(tsLockDao.acquireLock(any(TSLock.class))).thenReturn(false);

        assertFalse(service.acquireLock("lock1", "owner2"));
    }

    // ==================== releaseLock ====================

    @Test
    void releaseLock_returnsDaoResult() {
        when(tsLockDao.releaseLock("lock1")).thenReturn(true);

        assertTrue(service.releaseLock("lock1"));
        verify(tsLockDao).releaseLock("lock1");
    }

    // ==================== releaseAllLocks ====================

    @Test
    void releaseAllLocks_releasesEachHeldLock() {
        TSLock l1 = new TSLock();
        l1.setLockName("lock1");
        TSLock l2 = new TSLock();
        l2.setLockName("lock2");
        when(tsLockDao.getAllLocks()).thenReturn(List.of(l1, l2));
        when(tsLockDao.releaseLock(anyString())).thenReturn(true);

        assertEquals(2, service.releaseAllLocks());
        verify(tsLockDao).releaseLock("lock1");
        verify(tsLockDao).releaseLock("lock2");
    }

    @Test
    void releaseAllLocks_noLocks_returnsZero() {
        when(tsLockDao.getAllLocks()).thenReturn(null);

        assertEquals(0, service.releaseAllLocks());
        verify(tsLockDao, never()).releaseLock(anyString());
    }

    // ==================== releaseAllLocksByOwner ====================

    @Test
    void releaseAllLocksByOwner_releasesOwnerLocks() {
        TSLock l1 = new TSLock();
        l1.setLockName("lock1");
        l1.setLockOwner("owner1");
        when(tsLockDao.getLocksByOwner(anyString())).thenReturn(List.of(l1));
        when(tsLockDao.releaseLock("lock1")).thenReturn(true);

        assertEquals(1, service.releaseAllLocksByOwner("owner1"));
        verify(tsLockDao).releaseLock("lock1");
    }

    @Test
    void releaseAllLocksByOwner_noOwnerLocks_returnsZero() {
        when(tsLockDao.getLocksByOwner(anyString())).thenReturn(null);

        assertEquals(0, service.releaseAllLocksByOwner("owner1"));
    }
}
