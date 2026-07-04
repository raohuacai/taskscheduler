/**
 * api.js - Unified REST API layer
 * All endpoints and parameters remain identical to the original.
 * Authentication header is applied globally via $.ajaxSetup in app.js.
 */

const API = (function () {

    function _ajax(method, url, data) {
        const opts = { url, type: method };
        if (data !== undefined) {
            opts.contentType = 'application/json';
            opts.data = JSON.stringify(data);
        }
        return $.ajax(opts);
    }

    function _buildQuery(params) {
        if (!params) return '';
        const parts = [];
        Object.keys(params).forEach(function (k) {
            const v = params[k];
            if (v !== undefined && v !== null && v !== '') {
                parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
            }
        });
        return parts.length ? '?' + parts.join('&') : '';
    }

    return {
        // ── Stats ──────────────────────────────────────────────────────────
        getStats: function () {
            return _ajax('GET', '/api/stats');
        },

        // ── Workflow Definition ────────────────────────────────────────────
        getWorkflows: function (params) {
            return _ajax('GET', '/api/definition/workflow' + _buildQuery(params));
        },
        getWorkflowById: function (workflowId) {
            return _ajax('GET', '/api/definition/workflow/' + encodeURIComponent(workflowId));
        },
        createWorkflow: function (data) {
            return _ajax('POST', '/api/definition/workflow', data);
        },
        updateWorkflow: function (data) {
            return _ajax('PUT', '/api/definition/workflow', data);
        },
        deleteWorkflow: function (workflowId) {
            return _ajax('DELETE', '/api/definition/workflow/' + encodeURIComponent(workflowId));
        },

        // ── Task Definition ────────────────────────────────────────────────
        getTasksByWorkflow: function (workflowId) {
            return _ajax('GET', '/api/definition/task?workflowId=' + encodeURIComponent(workflowId));
        },
        getTask: function (taskId) {
            return _ajax('GET', '/api/definition/task/' + encodeURIComponent(taskId));
        },
        createTask: function (data) {
            return _ajax('POST', '/api/definition/task', data);
        },
        updateTask: function (data) {
            return _ajax('PUT', '/api/definition/task', data);
        },
        deleteTask: function (taskId) {
            return _ajax('DELETE', '/api/definition/task/' + encodeURIComponent(taskId));
        },

        // ── Excel Import / Export ──────────────────────────────────────────
        exportDefinitionExcel: function (params) {
            // Returns a raw fetch Promise (not jQuery) so we can handle binary blob
            const qs = params ? _buildQuery(params) : '';
            return fetch('/api/definition/excel' + qs, {
                method: 'GET',
                credentials: 'same-origin',
                headers: { 'Authorization': 'Bearer ' + getAuthToken() }
            });
        },
        importDefinitionExcel: function (formData) {
            // formData is a FormData object containing the xlsx file
            return fetch('/api/definition/excel/import', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Authorization': 'Bearer ' + getAuthToken() },
                body: formData
            });
        },

        // ── DAG Definition ─────────────────────────────────────────────────
        getDAG: function (workflowId) {
            return _ajax('GET', '/api/definition/dag?workflowId=' + encodeURIComponent(workflowId));
        },
        updateDAGTask: function (data) {
            return _ajax('PUT', '/api/definition/dag', data);
        },

        // ── Task Types ─────────────────────────────────────────────────────
        getTaskTypes: function (params) {
            return _ajax('GET', '/api/tasktype' + _buildQuery(params));
        },
        createTaskType: function (data) {
            return _ajax('POST', '/api/tasktype', data);
        },
        updateTaskType: function (data) {
            return _ajax('PUT', '/api/tasktype', data);
        },
        deleteTaskType: function (typeName, username) {
            return _ajax('DELETE', '/api/tasktype/' + encodeURIComponent(typeName) + '?lastUpdatedBy=' + encodeURIComponent(username));
        },

        // ── Scheduler ──────────────────────────────────────────────────────
        getSchedulers: function (params) {
            return _ajax('GET', '/api/scheduler/schedule' + _buildQuery(params));
        },
        getSchedulerById: function (workflowId) {
            return _ajax('GET', '/api/scheduler/schedule/' + encodeURIComponent(workflowId));
        },
        createScheduler: function (data) {
            return _ajax('POST', '/api/scheduler/schedule', data);
        },
        updateScheduler: function (data) {
            return _ajax('PUT', '/api/scheduler/schedule', data);
        },
        deleteScheduler: function (workflowId) {
            return _ajax('DELETE', '/api/scheduler/schedule/' + encodeURIComponent(workflowId));
        },
        startWorkflow: function (data) {
            return _ajax('POST', '/api/workflow/start', data);
        },

        // ── Global Params ──────────────────────────────────────────────────
        getGlobalParams: function () {
            return _ajax('GET', '/api/params/global');
        },
        updateGlobalParam: function (paramName, data) {
            return _ajax('PUT', '/api/params/global?paramName=' + paramName, data);
        },

        // ── Monitoring ─────────────────────────────────────────────────────
        getWorkflowInstances: function (params) {
            return _ajax('GET', '/api/workflow/workflows' + _buildQuery(params));
        },
        getInstanceTasks: function (instanceId) {
            return _ajax('GET', '/api/workflow/tasks/' + encodeURIComponent(instanceId));
        },
        retryTask: function (taskInstanceId) {
            return _ajax('POST', '/api/workflow/retry', { taskInstanceId });
        },
        skipTask: function (taskInstanceId) {
            return _ajax('POST', '/api/workflow/skip', { taskInstanceId });
        },
        cancelTask: function (taskInstanceId) {
            return _ajax('POST', '/api/workflow/canceltask', { taskInstanceId });
        },
        recoverWorkflow: function (instanceId) {
            return _ajax('POST', '/api/workflow/recover', { instanceId });
        },
        cancelWorkflow: function (instanceId) {
            return _ajax('POST', '/api/workflow/cancel', { instanceId });
        },

        // ── Task Instance Params & Logs ────────────────────────────────────
        getTaskParams: function (taskInstanceId) {
            return _ajax('GET', '/api/params/task?taskInstanceId=' + encodeURIComponent(taskInstanceId));
        },
        getTaskLogs: function (taskInstanceId) {
            return _ajax('GET', '/api/workflow/tasklog?taskInstanceId=' + encodeURIComponent(taskInstanceId));
        },

        // ── Locks ──────────────────────────────────────────────────────────
        getLocks: function (params) {
            return _ajax('GET', '/api/lock/list' + _buildQuery(params));
        },
        releaseLock: function (lockName) {
            return _ajax('POST', '/api/lock/release', { lockName });
        },
        releaseAllLocks: function () {
            return _ajax('POST', '/api/lock/releaseAll');
        },
        releaseAllLocksByOwner: function (lockOwner) {
            return _ajax('POST', '/api/lock/releaseByOwner', { lockOwner });
        },

        // ── History ────────────────────────────────────────────────────────
        getHistory: function (params) {
            return _ajax('GET', '/api/history' + _buildQuery(params));
        },
	
	// ── DAG Instance (Monitor) ─────────────────────────────────────────
        getInstanceDAG: function (instanceId) {
            return fetch('/api/params/dag/' + instanceId, {
                headers: { 'Authorization': 'Bearer ' + getAuthToken() }
            }).then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            });
        },
        getInstanceDAGAlt: function (instanceId) {
            return fetch('/api/params/dag?wfInstanceId=' + encodeURIComponent(instanceId), {
                headers: { 'Authorization': 'Bearer ' + getAuthToken() }
            }).then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            });
        },

        // ── User Management ────────────────────────────────────────────────
        getUsers: function () {
            return _ajax('GET', '/api/users');
        },
        createUser: function (data) {
            return _ajax('POST', '/api/users', data);
        },
        updateUser: function (data) {
            return _ajax('PUT', '/api/users', data);
        },
        deleteUser: function (username) {
            return _ajax('DELETE', '/api/users/' + encodeURIComponent(username));
        },
        resetUserPassword: function (data) {
            return _ajax('POST', '/api/users/resetpwd', data);
        },
        changeOwnPassword: function (data) {
            return _ajax('POST', '/api/users/changepwd', data);
        }
    };
})();
