/*
 * Description: Contract for task-type registry CRUD
 * Author: Rock Rao
 * Date: 3/23/2024
 * Version: 1.0
 */
package com.ts.service;

import com.github.pagehelper.PageInfo;
import com.ts.model.entity.TaskType;
import com.ts.task.TaskFactory;
/**
 * Contract for task-type registry management.
 *
 * <p>Task types map a logical type name (e.g., {@code SHELL}, {@code HTTP})
 * to a fully-qualified Java class name. The registry is queried by
 * {@link com.ts.task.DefaultTaskFactory} to instantiate tasks at runtime.
 */
public interface TaskTypeService {
    void setTaskFactory(TaskFactory taskFactory);

    boolean registerTaskType(TaskType taskType);

    boolean updateTaskType(TaskType taskType);

    boolean unregisterTaskType(String typeName);

    PageInfo<TaskType> search(String typeGroup, String typeName, int pageNum, int pageSize, String orderBy);

    TaskType getTaskType(String typeName);

}