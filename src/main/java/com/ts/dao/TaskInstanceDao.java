/*
 * Description: DAO interface for TaskInstance lifecycle management
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.dao;

import com.ts.model.entity.TaskInstance;
import org.apache.ibatis.session.SqlSession;

import java.util.List;
/**
 * DAO interface for {@link com.ts.model.entity.TaskInstance} lifecycle operations.
 *
 * <p>Covers creation, status updates (with and without error flag), and
 * retrieval by instance ID or workflow instance ID.
 */
public interface TaskInstanceDao {
    // Existing methods
    TaskInstance getTaskInstance(String taskInstanceId);

    List<TaskInstance> getTaskInstancesByWfInstanceId(String wfInstanceId);

    String createTaskInstance(TaskInstance taskInstance);

    boolean updateStatusToError(TaskInstance taskInstance);

    boolean updateStatusToRunning(TaskInstance taskInstance);

    boolean updateStatus(TaskInstance taskInstance);

    // New methods for transaction support
    TaskInstance getTaskInstance(String taskInstanceId, SqlSession session);

    List<TaskInstance> getTaskInstancesByWfInstanceId(String wfInstanceId, SqlSession session);

    String createTaskInstance(TaskInstance taskInstance, SqlSession session);

    boolean updateStatusToError(TaskInstance taskInstance, SqlSession session);

    boolean updateStatusToRunning(TaskInstance taskInstance, SqlSession session);

    boolean updateStatus(TaskInstance taskInstance, SqlSession session);
}