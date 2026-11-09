/*
 * Description: MyBatis implementation of TSLockDao for distributed lock persistence
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao.impl;

import com.ts.common.utils.ParamUtils;
import com.ts.dao.TSLockDao;
import com.ts.mapper.TSLockMapper;
import com.ts.model.entity.TSLock;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
/**
 * MyBatis-based implementation of {@link com.ts.dao.TSLockDao} for distributed
 * lock persistence.
 */
public class TSLockDaoImpl implements TSLockDao {
    private static final Logger logger = LogManager.getLogger(TSLockDaoImpl.class);
    private final SqlSessionFactory sqlSessionFactory;

    /**
     * Constructor that injects the MyBatis SqlSessionFactory
     *
     * @param sqlSessionFactory the MyBatis SqlSessionFactory used to open sessions
     */
    public TSLockDaoImpl(SqlSessionFactory sqlSessionFactory) {
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    /**
     * Acquires the lock.
     *
     * @param lock  lock value
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean acquireLock(TSLock lock) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TSLockMapper mapper = sqlSession.getMapper(TSLockMapper.class);
            // Exclusive lock is keyed by lock_name alone: if a record already exists
            // for this lock_name, another task/workflow holds the lock -> fail fast
            // (avoids hitting the unique-index exception in the normal case).
            Integer existing = mapper.checkLockExists(lock.getLockName());
            if (existing != null && existing > 0) {
                logger.debug("Lock '{}' is already held by another task/workflow", lock.getLockName());
                return false;
            }
            int result = mapper.insertLock(lock);
            sqlSession.commit();
            return result > 0;
        } catch (Exception e) {
            // Concurrency backstop: between checkLockExists and insertLock another session
            // may have inserted the same lock_name; UK_TS_LOCK_NAME then rejects the insert.
            logger.warn("Failed to acquire lock: {}", lock);
            return false;
        }
    }

    @Override
    /**
     * Releases the lock.
     *
     * @param lockName  lockName name
     * @return {@code true} if successful, {@code false} otherwise
     */
    public boolean releaseLock(String lockName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TSLockMapper mapper = sqlSession.getMapper(TSLockMapper.class);
            int result = mapper.deleteLock(lockName);
            sqlSession.commit();
            //return result > 0;
            return true;
        } catch (Exception e) {
            logger.error("Failed to release lock: name={}", lockName, e);
            return false;
        }
    }

    @Override
    /**
     * Returns the all locks.
     *
     * @return a list of matching records
     */
    public List<TSLock> getAllLocks() {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TSLockMapper mapper = sqlSession.getMapper(TSLockMapper.class);
            return mapper.selectAllLocks();
        } catch (Exception e) {
            logger.error("Error getting all locks", e);
            return List.of();
        }
    }

    @Override
    /**
     * Returns the locks by owner.
     *
     * @param lockOwner  lockOwner value
     * @return a list of matching records
     */
    public List<TSLock> getLocksByOwner(String lockOwner) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TSLockMapper mapper = sqlSession.getMapper(TSLockMapper.class);
            return mapper.selectLocksByOwner(ParamUtils.likePattern(lockOwner));
        } catch (Exception e) {
            logger.error("Error getting locks by owner: {}", lockOwner, e);
            return List.of();
        }
    }

    @Override
    /**
     * Returns the locks by name.
     *
     * @param lockName  lockName name
     * @return a list of matching records
     */
    public List<TSLock> getLocksByName(String lockName) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            TSLockMapper mapper = sqlSession.getMapper(TSLockMapper.class);
            return mapper.selectLocksByName(ParamUtils.likePattern(lockName));
        } catch (Exception e) {
            logger.error("Error getting locks by name: {}", lockName, e);
            return List.of();
        }
    }

}