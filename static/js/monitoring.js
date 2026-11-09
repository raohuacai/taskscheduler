/**
 * monitoring.js - Monitoring module & History module
 */

// taskInstanceId -> task name, populated when the Tasks modal renders.
// Confirmation prompts use the name instead of the internal instance id.
let monitorTaskNames = {};

// wfInstanceId -> workflow name, populated when the monitoring list renders.
let monitorWorkflowNames = {};

// The confirmation dialog and toast render HTML, so escape names before use.
function _escapeMonitorHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

// ═══════════════════════════════════════════════════════════════
//  MONITORING MODULE
// ═══════════════════════════════════════════════════════════════

function initMonitoringModule() {
    let currentPage   = 1;
    let pageSize      = 10;
    let orderBy       = 'START_DATE';
    let sortDirection = 'DESC';
    const today = getLocalToday();
    $('#monitor-start-date').val(today);

    $('#monitor-search-btn').on('click', function () {
        currentPage = 1;
        loadMonitoringData(currentPage, pageSize, orderBy, sortDirection);
    });

    $('#monitor-reset-btn').on('click', function () {
        $('#monitor-tp-instance-id').val('');
        $('#monitor-workflow-id').val('');
        $('#monitor-workflow-name').val('');
        $('#monitor-instance-id').val('');
        $('#monitor-parent-instance-id').val('');
        $('#monitor-status').val('');
        $('#monitor-start-date').val(today);
        $('#monitor-end-date').val('');
        currentPage = 1; orderBy = 'START_DATE'; sortDirection = 'DESC';
        loadMonitoringData(currentPage, pageSize, orderBy, sortDirection);
        updateSortIcons(orderBy, sortDirection, 'monitor-');
    });

    $('#monitor-pagination').on('click', '.page-link', function (e) {
        e.preventDefault();
        const p = $(this).data('page');
        if (p) { currentPage = p; loadMonitoringData(currentPage, pageSize, orderBy, sortDirection); }
    });

    $('#monitor-table').on('click', 'th .th-content, th .sort-icon', function () {
        let column = $(this).hasClass('th-content')
            ? $(this).data('column')
            : $(this).prev('.th-content').data('column');

        if (orderBy === column) {
            sortDirection = sortDirection === 'ASC' ? 'DESC' : 'ASC';
        } else {
            orderBy = column;
            sortDirection = column === 'START_DATE' ? 'DESC' : 'ASC';
        }
        updateSortIcons(orderBy, sortDirection, 'monitor-');
        currentPage = 1;
        loadMonitoringData(currentPage, pageSize, orderBy, sortDirection);
    });

    $(document).on('click', '#monitor-refresh-tasks-btn', function () {
        const instanceId = $('#monitor-current-instance-id').val();
        if (instanceId) loadTasksForInstance(instanceId, $('#monitor-current-wf-status').val());
    });
}

function loadMonitoringData(pageNum, pageSize, orderBy, sortDirection) {
    pageNum       = pageNum       || 1;
    pageSize      = pageSize      || 10;
    orderBy       = orderBy       || 'START_DATE';
    sortDirection = sortDirection || 'DESC';

    // 开始时间相同的实例顺序不固定；按 id 倒序作为次级排序键，保证分页和刷新后顺序稳定
    let orderClause = orderBy + ' ' + sortDirection;
    if (orderBy === 'START_DATE') orderClause += ', WF_INSTANCE_ID DESC';

    const params = { pageNum, pageSize, orderBy: orderClause };
    const workflowId          = $('#monitor-workflow-id').val();
    const workflowName        = $('#monitor-workflow-name').val();
    const instanceId          = $('#monitor-instance-id').val();
    const parentInstanceId    = $('#monitor-parent-instance-id').val();
    const topParentInstanceId = $('#monitor-tp-instance-id').val();
    const status              = $('#monitor-status').val();
    const startDateFrom       = $('#monitor-start-date').val();
    const startDateTo         = $('#monitor-end-date').val();

    if (workflowId)          params.workflowId          = workflowId;
    if (workflowName)        params.workflowName        = workflowName;
    if (instanceId)          params.wfInstanceId        = instanceId;
    if (parentInstanceId)    params.parentWfInstanceId  = parentInstanceId;
    if (topParentInstanceId) params.topParentInstanceId = topParentInstanceId;
    if (status)              params.status              = status;
    if (startDateFrom)       params.startDateFrom       = startDateFrom;
    if (startDateTo)         params.startDateTo         = startDateTo;

    $('#monitor-table tbody').html('<tr><td colspan="10" class="text-center">Loading workflow instances...</td></tr>');

    API.getWorkflowInstances(params)
        .done(function (r) {
            displayWorkflowInstances(r);
            buildPagination(r, pageNum, 'monitor-pagination');
            updateSortIcons(orderBy, sortDirection, 'monitor-');
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load workflow instances';
            $('#monitor-table tbody').html(`<tr><td colspan="10" class="text-center text-danger">${msg}</td></tr>`);
            $('#monitor-pagination').empty();
        });
}

function displayWorkflowInstances(response) {
    $('#monitor-table tbody').empty();
    monitorWorkflowNames = {};
    const instances = response.records;
    if (!instances || instances.length === 0) {
        $('#monitor-table tbody').html('<tr><td colspan="10" class="text-center">No workflow instances found</td></tr>');
        return;
    }

    instances.forEach(function (inst) {
        monitorWorkflowNames[inst.wfInstanceId] = inst.workflowName || inst.workflowId || inst.wfInstanceId;
        const startDate = inst.startDate ? formatLocaleDateTime(inst.startDate) : 'N/A';
        const endDate   = inst.endDate   ? formatLocaleDateTime(inst.endDate)   : 'N/A';
        const duration  = calcDurationHM(inst.startDate, inst.endDate);

        const statusBadgeClass = { COMPLETED:'bg-success', RUNNING:'bg-primary', WAITING:'bg-info', ERROR:'bg-danger', CANCELED:'bg-warning' }[inst.status] || 'bg-secondary';
        const taskErrClass     = { Y:'bg-danger', N:'bg-success' }[inst.taskErrorFlag] || 'bg-secondary';
		
		let actionButtons = `
            <button class="btn btn-sm btn-outline-info view-monitor-tasks-btn me-1" data-id="${inst.wfInstanceId}" data-wf-status="${inst.status}" title="View Tasks">
                <i class="bi bi-list-task"></i> Tasks
            </button>`;

        if (inst.status !== 'SKIPPED') {
            actionButtons += `<button class="btn btn-sm btn-outline-secondary view-monitor-dag-btn me-1" data-id="${inst.wfInstanceId}" title="View DAG">
                <i class="bi bi-diagram-3"></i> DAG
            </button>`;
            actionButtons += `<button class="btn btn-sm btn-outline-secondary view-monitor-gantt-btn me-1" data-id="${inst.wfInstanceId}" data-tp-id="${inst.topParentInstanceId || inst.wfInstanceId}" title="View Gantt">
                <i class="bi bi-bar-chart-steps"></i> Gantt
            </button>`;
        }
        if (inst.status === 'RUNNING' || inst.status === 'WAITING') {
            actionButtons += `<button class="btn btn-sm btn-outline-primary recover-wf-btn me-1" data-id="${inst.wfInstanceId}" title="Recover Workflow">
                <i class="bi bi-arrow-clockwise"></i>
            </button>`;
            actionButtons += `<button class="btn btn-sm btn-outline-danger cancel-wf-btn me-1" data-id="${inst.wfInstanceId}" title="Cancel Workflow">
                <i class="bi bi-x-circle"></i>
            </button>`;
        }

        $('#monitor-table tbody').append(`
            <tr>
                <td>${inst.topParentInstanceId || 'N/A'}</td>
                <td>${inst.parentWfInstanceId || 'N/A'}</td>
                <td>${inst.wfInstanceId}</td>
                <td>${inst.workflowId || 'N/A'}</td>
                <td>${inst.workflowName || 'N/A'}</td>
                <td>${startDate}</td>
                <td>${endDate}</td>
                <td>${duration}</td>
                <td><span class="badge ${statusBadgeClass}">${inst.status}</span></td>
                <td><span class="badge ${taskErrClass}">${inst.taskErrorFlag}</span></td>
                <td>${actionButtons}</td>
            </tr>`);
    });

    attachMonitorButtonHandlers();
}

function attachMonitorButtonHandlers() {
    $('.view-monitor-tasks-btn').on('click', function () { openTasksModal($(this).data('id'), $(this).data('wfStatus')); });
    $('.view-monitor-dag-btn').on('click',   function () { viewWorkflowDag($(this).data('id')); });
    $('.view-monitor-gantt-btn').on('click', function () { openGanttModal($(this).data('tpId')); });
    $('.recover-wf-btn').on('click',         function () { recoverWorkflow($(this).data('id')); });
    $('.cancel-wf-btn').on('click',          function () { cancelWorkflow($(this).data('id')); });
}

function openTasksModal(instanceId, wfStatus) {
    $('#monitor-current-instance-id').val(instanceId);
    $('#monitor-current-wf-status').val(wfStatus || '');
    $('#monitor-instance-id-display').text(instanceId);
    $('#monitor-tasks-modal').modal('show');
    loadTasksForInstance(instanceId, wfStatus);

    $('#monitor-refresh-tasks-btn').off('click').on('click', function () {
        const id = $('#monitor-current-instance-id').val();
        if (id) loadTasksForInstance(id, $('#monitor-current-wf-status').val());
    });
}

function loadTasksForInstance(instanceId, wfStatus) {
    $('#monitor-tasks-table tbody').html('<tr><td colspan="9" class="text-center">Loading tasks...</td></tr>');
    $('#monitor-refresh-tasks-btn').prop('disabled', true)
        .html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Loading...');

    // Always fetch the latest workflow status from the server to avoid stale cached values
    var wfStatusPromise = API.getWorkflowInstances({ wfInstanceId: instanceId, pageSize: 1 })
        .then(function (r) {
            var rec = r && r.records && r.records[0];
            return rec ? (rec.status || '').toUpperCase() : (wfStatus || '');
        })
        .catch(function () { return wfStatus || ''; });

    $.when(API.getInstanceTasks(instanceId), wfStatusPromise)
        .done(function (tasksResult, latestWfStatus) {
            // $.when passes ajax results as arrays when multiple deferreds are used
            var tasks = Array.isArray(tasksResult) ? tasksResult[0] : tasksResult;
            $('#monitor-current-wf-status').val(latestWfStatus);
            displayInstanceTasks(tasks, latestWfStatus);
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load tasks';
            $('#monitor-tasks-table tbody').html(`<tr><td colspan="9" class="text-center text-danger">${msg}</td></tr>`);
        })
        .always(function () {
            $('#monitor-refresh-tasks-btn').prop('disabled', false)
                .html('<i class="bi bi-arrow-clockwise"></i> Refresh');
        });
}

function displayInstanceTasks(tasks, wfStatus) {
    $('#monitor-tasks-table tbody').empty();
    monitorTaskNames = {};
    if (!tasks || tasks.length === 0) {
        $('#monitor-tasks-table tbody').html('<tr><td colspan="9" class="text-center">No tasks found for this workflow instance</td></tr>');
        return;
    }

    // Retry/Skip are only allowed when the workflow itself is still active (RUNNING or ERROR)
    const wfAllowsTaskAction = ['RUNNING', 'ERROR'].includes((wfStatus || '').toUpperCase());

    tasks.forEach(function (task) {
        monitorTaskNames[task.taskInstanceId] = task.taskName || task.taskId || task.taskInstanceId;
        const startDate   = task.startDate ? formatLocaleDateTime(task.startDate) : 'N/A';
        const endDate     = task.endDate   ? formatLocaleDateTime(task.endDate)   : 'N/A';
        const errorFlag   = task.errorFlag === 'Y'
            ? '<span class="text-danger"><i class="bi bi-exclamation-triangle"></i> Yes</span>' : 'No';
        const statusCls   = { COMPLETED:'bg-success', RUNNING:'bg-primary', WAITING:'bg-info', ERROR:'bg-danger', CANCELED:'bg-warning', TIMEOUT:'bg-warning text-dark' }[task.status] || 'bg-secondary';

        let btns = `<button class="btn btn-sm btn-outline-info monitor-task-details-btn me-1" data-id="${task.taskInstanceId}" title="Task Details"><i class="bi bi-info-circle"></i></button>`;
        // Retry: ERROR | TIMEOUT | CANCELED — only when workflow is still active
        if (wfAllowsTaskAction && ['ERROR','TIMEOUT','CANCELED'].includes(task.status)) {
            btns += `<button class="btn btn-sm btn-outline-primary monitor-retry-task-btn me-1" data-id="${task.taskInstanceId}" title="Retry Task"><i class="bi bi-arrow-repeat"></i></button>`;
        }
        // Skip: ERROR | WAITING | TIMEOUT | CANCELED — only when workflow is still active
        if (wfAllowsTaskAction && ['ERROR','WAITING','TIMEOUT','CANCELED'].includes(task.status)) {
            btns += `<button class="btn btn-sm btn-outline-warning monitor-skip-task-btn me-1" data-id="${task.taskInstanceId}" title="Skip Task"><i class="bi bi-skip-forward"></i></button>`;
        }
        // Cancel: RUNNING
        if (task.status === 'RUNNING') {
            btns += `<button class="btn btn-sm btn-outline-danger monitor-cancel-task-btn" data-id="${task.taskInstanceId}" title="Cancel Task"><i class="bi bi-x-circle"></i></button>`;
        }

        $('#monitor-tasks-table tbody').append(`
            <tr>
                <td>${task.taskInstanceId}</td>
                <td>${task.taskName || task.taskId || 'N/A'}</td>
                <td><code>${task.taskType || '-'}</code></td>
                <td>${startDate}</td>
                <td>${endDate}</td>
                <td><span class="badge ${statusCls}">${task.status}</span></td>
                <td>${errorFlag}</td>
                <td class="error-message-cell">${task.errorMessage || ''}</td>
                <td>${btns}</td>
            </tr>`);
    });

    attachMonitorTaskButtonHandlers();
}

function attachMonitorTaskButtonHandlers() {
    $('.monitor-task-details-btn').on('click', function () { showTaskDetails($(this).data('id')); });
    $('.monitor-retry-task-btn').on('click',   function () { retryTask($(this).data('id')); });
    $('.monitor-skip-task-btn').on('click',    function () { skipTask($(this).data('id')); });
    $('.monitor-cancel-task-btn').on('click',  function () { cancelTask($(this).data('id')); });
}

function _reloadMonitorAfterAction() {
    const cp = parseInt($('#monitor-pagination .page-item.active .page-link').data('page')) || 1;
    const { orderBy, sortDirection } = getCurrentSortFromIcons('monitor-');
    loadMonitoringData(cp, 10, orderBy || 'START_DATE', sortDirection || 'DESC');
}

function retryTask(taskId) {
    const taskName = _escapeMonitorHtml(monitorTaskNames[taskId] || taskId);
    showConfirmDialog({
        title: 'Retry Task', message: `Are you sure you want to retry task ${taskName}?`, okButtonClass: 'btn-primary',
        onConfirm: function () {
            const t = showToast('Processing', 'Retrying task...', 'info', { autohide: false });
            API.retryTask(taskId)
                .done(function (r) {
                    t.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `Task ${taskName} retry initiated successfully`, 'success');
                        const id = $('#monitor-current-instance-id').val();
                        if (id) loadTasksForInstance(id, $('#monitor-current-wf-status').val());
                    } else { showToast('Error', `Failed to retry task: ${(r && r.error) || 'Unknown error'}`, 'danger'); }
                })
                .fail(function (xhr) { t.hide(); showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to retry task', 'danger'); });
        }
    });
}

function skipTask(taskId) {
    const taskName = _escapeMonitorHtml(monitorTaskNames[taskId] || taskId);
    showConfirmDialog({
        title: 'Skip Task', message: `Are you sure you want to skip task ${taskName}?`, okButtonClass: 'btn-warning',
        onConfirm: function () {
            const t = showToast('Processing', 'Skipping task...', 'info', { autohide: false });
            API.skipTask(taskId)
                .done(function (r) {
                    t.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `Task ${taskName} skipped successfully`, 'success');
                        const id = $('#monitor-current-instance-id').val();
                        if (id) loadTasksForInstance(id, $('#monitor-current-wf-status').val());
                    } else { showToast('Error', `Failed to skip task: ${(r && r.error) || 'Unknown error'}`, 'danger'); }
                })
                .fail(function (xhr) { t.hide(); showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to skip task', 'danger'); });
        }
    });
}

function cancelTask(taskId) {
    const taskName = _escapeMonitorHtml(monitorTaskNames[taskId] || taskId);
    showConfirmDialog({
        title: 'Cancel Task', message: `Are you sure you want to cancel task ${taskName}?`, okButtonClass: 'btn-danger',
        onConfirm: function () {
            const t = showToast('Processing', 'Cancelling task...', 'info', { autohide: false });
            API.cancelTask(taskId)
                .done(function (r) {
                    t.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `Task ${taskName} cancelled successfully`, 'success');
                        const id = $('#monitor-current-instance-id').val();
                        if (id) loadTasksForInstance(id, $('#monitor-current-wf-status').val());
                    } else { showToast('Error', `Failed to cancel task: ${(r && r.error) || 'Unknown error'}`, 'danger'); }
                })
                .fail(function (xhr) { t.hide(); showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to cancel task', 'danger'); });
        }
    });
}

function recoverWorkflow(instanceId) {
    const workflowName = _escapeMonitorHtml(monitorWorkflowNames[instanceId] || instanceId);
    showConfirmDialog({
        title: 'Recover Workflow', message: `Are you sure you want to recover workflow ${workflowName}?`, okButtonClass: 'btn-primary',
        onConfirm: function () {
            const t = showToast('Processing', 'Recovering workflow...', 'info', { autohide: false });
            API.recoverWorkflow(instanceId)
                .done(function (r) {
                    t.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `Workflow ${workflowName} recovery initiated successfully`, 'success');
                        _reloadMonitorAfterAction();
                    } else { showToast('Error', `Failed to recover workflow: ${(r && r.error) || 'Unknown error'}`, 'danger'); }
                })
                .fail(function (xhr) { t.hide(); showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to recover workflow', 'danger'); });
        }
    });
}

function cancelWorkflow(instanceId) {
    const workflowName = _escapeMonitorHtml(monitorWorkflowNames[instanceId] || instanceId);
    showConfirmDialog({
        title: 'Cancel Workflow', message: `Are you sure you want to cancel workflow ${workflowName}?`, okButtonClass: 'btn-danger',
        onConfirm: function () {
            const t = showToast('Processing', 'Cancelling workflow...', 'info', { autohide: false });
            API.cancelWorkflow(instanceId)
                .done(function (r) {
                    t.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `Workflow ${workflowName} cancelled successfully`, 'success');
                        _reloadMonitorAfterAction();
                    } else { showToast('Error', `Failed to cancel workflow: ${(r && r.error) || 'Unknown error'}`, 'danger'); }
                })
                .fail(function (xhr) { t.hide(); showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to cancel workflow', 'danger'); });
        }
    });
}

function viewWorkflowDag(instanceId) {
    if (!instanceId) { showToast('Error', 'No workflow instance ID provided', 'danger'); return; }
    if (!getAuthToken()) { showToast('Error', 'Authentication required', 'danger'); window.location.href = 'login.html'; return; }
    localStorage.setItem('selectedWfInstanceId', instanceId);
    showModalWithCloseButton('dagviewer.html', '90vw', '90vh');
}

/**
 * Opens a modal containing an iframe.
 *
 * @param {string} url    page to load in the iframe
 * @param {number|string} width  number = pixels, string = any CSS length (e.g. '90vw')
 * @param {number|string} height number = pixels, string = any CSS length (e.g. '90vh')
 */
function showModalWithCloseButton(url, width, height) {
    const overlay = document.createElement('div');
    overlay.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background-color:rgba(0,0,0,0.5);z-index:9998;';

    const cssWidth  = (typeof width  === 'number') ? width  + 'px' : width;
    const cssHeight = (typeof height === 'number') ? height + 'px' : height;

    const container = document.createElement('div');
    container.style.cssText = `position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);width:${cssWidth};height:${cssHeight};background:white;border-radius:4px;box-shadow:0 2px 10px rgba(0,0,0,0.2);z-index:9999;`;

    const closeBtn = document.createElement('button');
    closeBtn.innerHTML = '&times;';
    closeBtn.style.cssText = 'position:absolute;top:10px;right:10px;width:30px;height:30px;background:transparent;border:none;font-size:20px;cursor:pointer;color:#666;';
    closeBtn.onclick = function () { document.body.removeChild(overlay); document.body.removeChild(container); };

    const title = document.createElement('div');
    title.textContent = 'Workflow Instance DAG Viewer';
    title.style.cssText = 'position:absolute;top:10px;left:30px;font-size:16px;font-weight:600;color:#333;user-select:none;';

    const iframe = document.createElement('iframe');
    iframe.src = url;
    iframe.style.cssText = 'width:100%;height:calc(100% - 40px);border:none;margin-top:40px;';

    container.appendChild(closeBtn);
    container.appendChild(title);
    container.appendChild(iframe);
    document.body.appendChild(overlay);
    document.body.appendChild(container);
    overlay.onclick = closeBtn.onclick;
}

function showTaskDetails(taskId) {
    $('#task-details-task-id').val(taskId);
    $('#task-details-modal-title').text(`Task Details: ${taskId}`);
    new bootstrap.Modal('#task-details-modal').show();
    loadTaskParams(taskId);
    loadTaskLogs(taskId);
}

function loadTaskParams(taskId) {
    $('#task-details-params').html('<div class="text-center py-4"><div class="spinner-border spinner-border-sm"></div><div class="text-muted mt-2">Loading parameters...</div></div>');
    API.getTaskParams(taskId)
        .done(function (r) {
            let content;
            if (r && r.pvalue) {
                try { content = `<pre class="m-0">${JSON.stringify(JSON.parse(r.pvalue), null, 2)}</pre>`; }
                catch (e) { content = `<pre class="m-0">${r.pvalue}</pre>`; }
            } else { content = '<div class="text-muted">No parameters available</div>'; }
            $('#task-details-params').html(content);
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load parameters';
            $('#task-details-params').html(`<div class="alert alert-danger py-2"><i class="bi bi-exclamation-circle"></i> ${msg}</div>`);
        });
}

function loadTaskLogs(taskId) {
    $('#task-details-logs').html('<div class="text-center py-4"><div class="spinner-border spinner-border-sm"></div><div class="text-muted mt-2">Loading logs...</div></div>');
    API.getTaskLogs(taskId)
        .done(function (r) {
            let content;
            if (r && r.length > 0) {
                content = '<div class="log-list">';
                r.forEach(function (log) {
                    content += `<div class="log-entry mb-2"><div class="log-meta text-muted small">${new Date(log.creationDate).toLocaleString()} ${log.message}</div></div>`;
                });
                content += '</div>';
            } else { content = '<div class="text-muted">No logs available</div>'; }
            $('#task-details-logs').html(content);
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load logs';
            $('#task-details-logs').html(`<div class="alert alert-danger py-2"><i class="bi bi-exclamation-circle"></i> ${msg}</div>`);
        });
}

// ═══════════════════════════════════════════════════════════════
//  HISTORY MODULE
// ═══════════════════════════════════════════════════════════════

function initHistoryModule() {
    let currentPage = 1;
    let pageSize    = 15;
    const today = getLocalToday();
    $('#history-start-date').val(today);

    $('#history-search-btn').on('click', function () {
        currentPage = 1;
        loadHistoryData(currentPage, pageSize);
    });

    $('#history-reset-btn').on('click', function () {
        $('#history-tp-instance-id').val('');
        $('#history-parent-instance-id').val('');
        $('#history-workflow-id').val('');
        $('#history-workflow-name').val('');
        $('#history-instance-id').val('');
        $('#history-status').val('');
        $('#history-start-date').val(today);
        $('#history-end-date').val('');
        currentPage = 1;
        loadHistoryData(currentPage, pageSize);
    });

    $('#history-export-btn').on('click', function () { exportHistoryToCSV(); });

    $('#history-pagination').on('click', '.page-link', function (e) {
        e.preventDefault();
        const p = $(this).data('page');
        if (p) { currentPage = p; loadHistoryData(currentPage, pageSize); }
    });
}

function loadHistoryData(pageNum, pageSize) {
    pageNum  = pageNum  || 1;
    pageSize = pageSize || 10;

    const params = { pageNum, pageSize };
    const topParentInstanceId = $('#history-tp-instance-id').val();
    const parentWfInstanceId  = $('#history-parent-instance-id').val();
    const workflowId          = $('#history-workflow-id').val();
    const workflowName        = $('#history-workflow-name').val();
    const instanceId          = $('#history-instance-id').val();
    const status              = $('#history-status').val();
    const startDateFrom       = $('#history-start-date').val();
    const startDateTo         = $('#history-end-date').val();

    if (topParentInstanceId) params.topParentInstanceId = topParentInstanceId;
    if (parentWfInstanceId)  params.parentWfInstanceId  = parentWfInstanceId;
    if (workflowId)          params.workflowId          = workflowId;
    if (workflowName)        params.workflowName        = workflowName;
    if (instanceId)          params.instanceId          = instanceId;
    if (status)              params.status              = status;
    if (startDateFrom)       params.startDateFrom       = startDateFrom;
    if (startDateTo)         params.startDateTo         = startDateTo;

    $('#history-table tbody').html('<tr><td colspan="15" class="text-center">Loading history data...</td></tr>');

    API.getHistory(params)
        .done(function (r) {
            displayHistoryData(r);
            buildPagination(r, pageNum, 'history-pagination');
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load workflow history';
            $('#history-table tbody').html(`<tr><td colspan="15" class="text-center text-danger">${msg}</td></tr>`);
            $('#history-pagination').empty();
        });
}

function displayHistoryData(response) {
    $('#history-table tbody').empty();
    const list = response.list || [];
    if (list.length === 0) {
        $('#history-table tbody').html('<tr><td colspan="15" class="text-center">No workflow history found</td></tr>');
        return;
    }

    list.forEach(function (inst) {
        const wfStart    = inst.startDate ? new Date(inst.startDate).toLocaleString() : '-';
        const wfEnd      = inst.endDate   ? new Date(inst.endDate).toLocaleString()   : '-';
        const wfDuration = calcDurationHM(inst.startDate, inst.endDate);
        const wfCls      = { COMPLETED:'bg-success', RUNNING:'bg-primary', ERROR:'bg-danger', CANCELED:'bg-warning', WAITING:'bg-info' }[inst.status] || 'bg-secondary';

        if (inst.tasks && inst.tasks.length > 0) {
            inst.tasks.forEach(function (task, idx) {
                const tStart = task.startDate ? new Date(task.startDate).toLocaleString() : '-';
                const tEnd   = task.endDate   ? new Date(task.endDate).toLocaleString()   : '-';
                const tCls   = { COMPLETED:'bg-success', RUNNING:'bg-primary', ERROR:'bg-danger', CANCELED:'bg-warning', WAITING:'bg-info' }[task.status] || 'bg-secondary';
                const errFlg = task.errorFlag === 'Y' ? '<span class="text-danger"><i class="bi bi-exclamation-triangle"></i> Yes</span>' : 'No';
                const rs     = inst.tasks.length;

                $('#history-table tbody').append(`
                    <tr>
                        ${idx===0?`<td rowspan="${rs}">${inst.topParentInstanceId||''}</td>`:''}
                        ${idx===0?`<td rowspan="${rs}">${inst.parentInstanceId||''}</td>`:''}
                        ${idx===0?`<td rowspan="${rs}">${inst.instanceId}</td>`:''}
                        ${idx===0?`<td rowspan="${rs}">${inst.workflowId||'-'}</td>`:''}
                        ${idx===0?`<td rowspan="${rs}">${inst.workflowName||'-'}</td>`:''}
                        ${idx===0?`<td rowspan="${rs}">${wfStart}</td>`:''}
                        ${idx===0?`<td rowspan="${rs}">${wfEnd}</td>`:''}
                        ${idx===0?`<td rowspan="${rs}"><span class="badge ${wfCls}">${inst.status}</span></td>`:''}
                        ${idx===0?`<td rowspan="${rs}">${wfDuration}</td>`:''}
                        <td>${task.taskInstanceId||'-'}</td>
                        <td>${task.taskName||'-'}</td>
                        <td><code>${task.taskType||'-'}</code></td>
                        <td>${tStart}</td>
                        <td>${tEnd}</td>
                        <td><span class="badge ${tCls}">${task.status}</span></td>
                        <td>${errFlg}</td>
                    </tr>`);
            });
        } else {
            $('#history-table tbody').append(`
                <tr>
                    <td>${inst.topParentInstanceId||''}</td>
                    <td>${inst.parentInstanceId||''}</td>
                    <td>${inst.instanceId}</td>
                    <td>${inst.workflowId||'-'}</td>
                    <td>${inst.workflowName||'-'}</td>
                    <td>${wfStart}</td>
                    <td>${wfEnd}</td>
                    <td><span class="badge ${wfCls}">${inst.status}</span></td>
                    <td>${wfDuration}</td>
                    <td colspan="7" class="text-center">No tasks found for this workflow</td>
                </tr>`);
        }
    });
}

function exportHistoryToCSV() {
    const params = { export: 'true' };
    const topParentInstanceId = $('#history-tp-instance-id').val();
    const parentWfInstanceId  = $('#history-parent-instance-id').val();
    const workflowId          = $('#history-workflow-id').val();
    const workflowName        = $('#history-workflow-name').val();
    const instanceId          = $('#history-instance-id').val();
    const status              = $('#history-status').val();
    const startDateFrom       = $('#history-start-date').val();
    const startDateTo         = $('#history-end-date').val();

    if (topParentInstanceId) params.topParentInstanceId = topParentInstanceId;
    if (parentWfInstanceId)  params.parentWfInstanceId  = parentWfInstanceId;
    if (workflowId)          params.workflowId          = workflowId;
    if (workflowName)        params.workflowName        = workflowName;
    if (instanceId)          params.instanceId          = instanceId;
    if (status)              params.status              = status;
    if (startDateFrom)       params.startDateFrom       = startDateFrom;
    if (startDateTo)         params.startDateTo         = startDateTo;

    const qs = Object.keys(params).map(k => encodeURIComponent(k) + '=' + encodeURIComponent(params[k])).join('&');

    const $btn = $('#history-export-btn');
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Exporting...');

    fetch('/api/history?' + qs, {
        method: 'GET',
        credentials: 'same-origin',
        headers: { 'Authorization': 'Bearer ' + getAuthToken() }
    }).then(function (res) {
        if (!res.ok) return res.text().then(function (t) { throw new Error(t || 'Server error ' + res.status); });
        const ct = res.headers.get('content-type');
        if (ct && ct.includes('application/json')) return res.json().then(function (d) { throw new Error(d.error || 'Unexpected response'); });
        return res.blob();
    }).then(function (blob) {
        const filename = `workflow_history_${new Date().toISOString().slice(0, 10)}.csv`;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', filename);
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        setTimeout(function () { URL.revokeObjectURL(url); }, 100);
    }).catch(function (err) {
        showToast('Error', 'Failed to export workflow history: ' + err.message, 'danger');
    }).finally(function () {
        $btn.prop('disabled', false).html('<i class="bi bi-file-earmark-excel"></i> Export CSV');
    });
}
