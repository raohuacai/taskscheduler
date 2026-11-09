package com.ts.dao.impl;

import com.ts.mapper.TSLockMapper;
import com.ts.model.entity.TSLock;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TSLockDaoImpl}, especially the F1 lock fix in
 * {@code acquireLock}: it first checks {@code checkLockExists(lockName)} and
 * fails fast when the lock name is already held (avoiding a DB exception in the
 * normal case), and relies on the lock_name primary key as a concurrency backstop.
 */
@ExtendWith(MockitoExtension.class)
class TSLockDaoImplTest {

    @Mock
    private SqlSessionFactory sqlSessionFactory;
    @Mock
    private SqlSession sqlSession;
    @Mock
    private TSLockMapper mapper;

    private TSLockDaoImpl dao;

    @BeforeEach
    void setUp() {
        when(sqlSessionFactory.openSession()).thenReturn(sqlSession);
        when(sqlSession.getMapper(TSLockMapper.class)).thenReturn(mapper);
        dao = new TSLockDaoImpl(sqlSessionFactory);
    }

    private TSLock lock(String name, String owner) {
        TSLock l = new TSLock();
        l.setLockName(name);
        l.setLockOwner(owner);
        return l;
    }

    // ==================== acquireLock ====================

    @Test
    void acquireLock_lockFree_insertsAndCommits() {
        when(mapper.checkLockExists("lock1")).thenReturn(0);
        when(mapper.insertLock(any(TSLock.class))).thenReturn(1);

        assertTrue(dao.acquireLock(lock("lock1", "owner1")));

        verify(mapper).checkLockExists("lock1");
        verify(mapper).insertLock(any(TSLock.class));
        verify(sqlSession).commit();
    }

    @Test
    void acquireLock_lockHeld_returnsFalseWithoutInsert() {
        when(mapper.checkLockExists("lock1")).thenReturn(1);

        assertFalse(dao.acquireLock(lock("lock1", "owner2")));

        verify(mapper, never()).insertLock(any(TSLock.class));
        verify(sqlSession, never()).commit();
    }

    @Test
    void acquireLock_concurrentConflict_insertFails_returnsFalse() {
        // another session inserted the same lock_name between check and insert
        when(mapper.checkLockExists("lock1")).thenReturn(0);
        when(mapper.insertLock(any(TSLock.class)))
                .thenThrow(new RuntimeException("Duplicate entry for key 'PRIMARY'"));

        assertFalse(dao.acquireLock(lock("lock1", "owner1")));
        verify(sqlSession, never()).commit();
    }

    @Test
    void acquireLock_checkExistsError_returnsFalse() {
        when(mapper.checkLockExists("lock1")).thenThrow(new RuntimeException("db down"));

        assertFalse(dao.acquireLock(lock("lock1", "owner1")));
        verify(mapper, never()).insertLock(any(TSLock.class));
    }

    // ==================== releaseLock ====================

    @Test
    void releaseLock_deletesAndReturnsTrue() {
        when(mapper.deleteLock("lock1")).thenReturn(1);

        assertTrue(dao.releaseLock("lock1"));
        verify(mapper).deleteLock("lock1");
        verify(sqlSession).commit();
    }

    @Test
    void releaseLock_error_returnsFalse() {
        when(mapper.deleteLock("lock1")).thenThrow(new RuntimeException("db down"));

        assertFalse(dao.releaseLock("lock1"));
    }

    // ==================== queries ====================

    @Test
    void getAllLocks_returnsAll() {
        when(mapper.selectAllLocks()).thenReturn(List.of(lock("l1", "o1")));

        assertEquals(1, dao.getAllLocks().size());
    }

    @Test
    void getAllLocks_error_returnsEmptyList() {
        when(mapper.selectAllLocks()).thenThrow(new RuntimeException("db down"));

        assertTrue(dao.getAllLocks().isEmpty());
    }

    @Test
    void getLocksByOwner_delegatesWithLikePattern() {
        when(mapper.selectLocksByOwner(anyString())).thenReturn(List.of(lock("l1", "o1")));

        assertEquals(1, dao.getLocksByOwner("o1").size());
        verify(mapper).selectLocksByOwner("%o1%");
    }

    @Test
    void getLocksByName_delegatesWithLikePattern() {
        when(mapper.selectLocksByName(anyString())).thenReturn(List.of(lock("l1", "o1")));

        assertEquals(1, dao.getLocksByName("l1").size());
        verify(mapper).selectLocksByName("%l1%");
    }
}
