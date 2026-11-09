/**
 * workflow.js - Workflow Definition & Task Definition modules
 */

// ═══════════════════════════════════════════════════════════════
//  WORKFLOW MODULE
// ═══════════════════════════════════════════════════════════════

function initWorkflowModule() {
    let currentPage   = 1;
    let pageSize      = 10;
    let orderBy       = 'WORKFLOW_NAME';
    let sortDirection = 'ASC';

    $('#add-workflow-btn').on('click', function () {
        $('#workflow-form-modal .modal-title').text('Create Workflow');
        $('#workflow-form')[0].reset();
        $('#workflow-id').val('');
        $('#workflow-name').prop('readonly', false).removeClass('bg-light text-muted');
        $('#workflow-status').val('Y');
        $('#workflow-parameters').val('{\n  "wfP1": "donot use param as key",\n  "wfP2": "value2"\n}');
        $('#workflow-form-modal').modal('show');
    });

    $('#save-workflow-btn').on('click', function () { saveWorkflow(); });
    $('#export-excel-btn').on('click', function () { exportWorkflowExcel(); });
    $('#import-excel-btn').on('click', function () { $('#import-excel-file').val(''); $('#import-excel-file').trigger('click'); });
    $('#import-excel-file').on('change', function () {
        const file = this.files[0];
        if (file) importWorkflowExcel(file);
    });

    $('#workflow-search-btn').on('click', function () {
        currentPage = 1;
        loadWorkflowData(currentPage, pageSize, orderBy, sortDirection);
    });

    $('#workflow-reset-btn').on('click', function () {
        $('#workflow-id-search').val('');
        $('#workflow-name-search').val('');
        currentPage = 1; orderBy = 'WORKFLOW_NAME'; sortDirection = 'ASC';
        loadWorkflowData(currentPage, pageSize, orderBy, sortDirection);
        updateSortIcons(orderBy, sortDirection);
    });

    $('#workflow-pagination').on('click', '.page-link', function (e) {
        e.preventDefault();
        const p = $(this).data('page');
        if (p) { currentPage = p; loadWorkflowData(currentPage, pageSize, orderBy, sortDirection); }
    });

    $('#workflow-table').on('click', 'th .th-content, th .sort-icon', function () {
        let column = $(this).hasClass('th-content')
            ? $(this).data('column')
            : $(this).prev('.th-content').data('column');

        if (orderBy === column) {
            sortDirection = sortDirection === 'ASC' ? 'DESC' : 'ASC';
        } else {
            orderBy = column; sortDirection = 'ASC';
        }
        updateSortIcons(orderBy, sortDirection);
        currentPage = 1;
        loadWorkflowData(currentPage, pageSize, orderBy, sortDirection);
    });
}

function loadWorkflowData(pageNum, pageSize, orderBy, sortDirection) {
    pageNum       = pageNum       || 1;
    pageSize      = pageSize      || 10;
    orderBy       = orderBy       || 'WORKFLOW_NAME';
    sortDirection = sortDirection || 'ASC';

    const workflowId   = $('#workflow-id-search').val();
    const workflowName = $('#workflow-name-search').val();

    const params = {
        pageNum,
        pageSize,
        orderBy: orderBy + ' ' + sortDirection
    };
    if (workflowId)   params.workflowId   = workflowId;
    if (workflowName) params.workflowName = workflowName;

    $('#workflow-table tbody').html('<tr><td colspan="7" class="text-center">Loading...</td></tr>');

    API.getWorkflows(params)
        .done(function (response) {
            displayWorkflows(response);
            buildPagination(response, pageNum, 'workflow-pagination');
            updateSortIcons(orderBy, sortDirection);
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load workflows';
            $('#workflow-table tbody').html(`<tr><td colspan="7" class="text-center text-danger">${msg}</td></tr>`);
            $('#workflow-pagination').empty();
        });
}

function displayWorkflows(response) {
    $('#workflow-table tbody').empty();
    const workflows = response.records;
    if (!workflows || workflows.length === 0) {
        $('#workflow-table tbody').html('<tr><td colspan="7" class="text-center">No workflows found</td></tr>');
        return;
    }
    workflows.forEach(function (wf) {
        const creationDate = wf.creationDate ? formatLocaleDateTime(wf.creationDate) : 'N/A';
        const params       = prettyJson(wf.pvalue);
        const statusBadge  = (wf.enableFlag === 'Y' || wf.enableFlag === true)
            ? '<span class="badge bg-success">Active</span>'
            : '<span class="badge bg-secondary">Inactive</span>';

        $('#workflow-table tbody').append(`
            <tr data-id="${wf.workflowId}">
                <td>${wf.workflowId}</td>
                <td>${wf.workflowName}</td>
                <td>${wf.workflowDesc || ''}</td>
                <td><pre class="m-0" style="max-width:450px;max-height:45px;overflow:auto">${params}</pre></td>
                <td>${creationDate}</td>
                <td>${statusBadge}</td>
                <td>
                    <button class="btn btn-sm btn-outline-info view-tasks-btn me-1" data-id="${wf.workflowId}" data-name="${wf.workflowName}">
                        <i class="bi bi-list-task"></i> Tasks
                    </button>
                    ${adminBtn(`<button class="btn btn-sm btn-outline-secondary edit-workflow-btn me-1" data-id="${wf.workflowId}">
                        <i class="bi bi-pencil"></i>
                    </button>`)}
                    ${adminBtn(`<button class="btn btn-sm btn-outline-danger delete-workflow-btn" data-id="${wf.workflowId}">
                        <i class="bi bi-trash"></i>
                    </button>`)}
                </td>
            </tr>`);
    });

    $('.edit-workflow-btn').on('click', function () { editWorkflow($(this).data('id')); });
    $('.delete-workflow-btn').on('click', function () {
        const id = $(this).data('id');
        showConfirmDialog({ message: `Are you sure you want to delete workflow ${id}?`, onConfirm: () => deleteWorkflow(id) });
    });
    $('.view-tasks-btn').on('click', function () {
        openTaskDefinitionModal($(this).data('id'), $(this).data('name'));
    });
}

function saveWorkflow() {
    const form = $('#workflow-form')[0];
    if (!form.checkValidity()) { form.reportValidity(); return; }
    if (!isValidJson($('#workflow-parameters').val())) {
        showToast('Error', 'Invalid JSON format in Parameters', 'danger'); return;
    }

    const workflowId = $('#workflow-id').val();
    const isNew      = !workflowId;
    const username   = getUsername();

    const wf = {
        workflowName: $('#workflow-name').val(),
        workflowDesc: $('#workflow-description').val(),
        pvalue      : $('#workflow-parameters').val(),
        enableFlag  : $('#workflow-status').val(),
        lastUpdatedBy: username
    };
    if (isNew)  { wf.createdBy = username; }
    else        { wf.workflowId = workflowId; }

    $('#save-workflow-btn').prop('disabled', true)
        .html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Saving...');

    const call = isNew ? API.createWorkflow(wf) : API.updateWorkflow(wf);
    call.done(function (r) {
        if (r && r.status === 'success') {
            $('#workflow-form-modal').modal('hide');
            const cp = parseInt($('#workflow-pagination .page-item.active .page-link').data('page')) || 1;
            const { orderBy, sortDirection } = getCurrentSortFromIcons();
            loadWorkflowData(cp, 10, orderBy || 'WORKFLOW_NAME', sortDirection || 'ASC');
            showToast('Success', `Workflow ${isNew ? 'created' : 'updated'} successfully`, 'success');
        } else {
            showToast('Error', `Failed to ${isNew ? 'create' : 'update'} workflow: ${(r && r.error) || 'Unknown error'}`, 'danger');
        }
    }).fail(function (xhr) {
        showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || `Failed to ${isNew ? 'create' : 'update'} workflow`, 'danger');
    }).always(function () {
        $('#save-workflow-btn').prop('disabled', false).text('Save');
    });
}

function editWorkflow(workflowId) {
    $('#workflow-form-modal .modal-title').text('Edit Workflow (Loading...)');
    $('#workflow-form-modal').modal('show');

    API.getWorkflowById(workflowId)
        .done(function (wf) {
            $('#workflow-form-modal .modal-title').text('Edit Workflow');
            if (wf && wf.workflowId) {
                $('#workflow-id').val(wf.workflowId);
                $('#workflow-name').val(wf.workflowName);
                $('#workflow-description').val(wf.workflowDesc || '');
                $('#workflow-parameters').val(prettyJson(wf.pvalue));
                $('#workflow-status').val(wf.enableFlag || 'A');
            } else {
                showToast('Error', 'Workflow not found', 'danger');
                $('#workflow-form-modal').modal('hide');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load workflow details', 'danger');
            $('#workflow-form-modal').modal('hide');
        });
}

function deleteWorkflow(workflowId) {
    API.deleteWorkflow(workflowId)
        .done(function (r) {
            if (r && r.status === 'success') {
                const cp = parseInt($('#workflow-pagination .page-item.active .page-link').data('page')) || 1;
                const { orderBy, sortDirection } = getCurrentSortFromIcons();
                loadWorkflowData(cp, 10, orderBy || 'WORKFLOW_NAME', sortDirection || 'ASC');
                showToast('Success', `Workflow ${workflowId} deleted successfully`, 'success');
            } else {
                showToast('Error', `Failed to delete workflow: ${(r && r.error) || 'Unknown error'}`, 'danger');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to delete workflow', 'danger');
        });
}

// ═══════════════════════════════════════════════════════════════
//  TASK DEFINITION MODULE
// ═══════════════════════════════════════════════════════════════

function initTaskModule() {
    $('#add-task-btn').on('click', function () {
        showTaskForm(null, $('#current-workflow-id').val());
    });
    $('#save-task-btn').on('click', function () { saveTask(); });
}

function openTaskDefinitionModal(workflowId, workflowName) {
    $('#current-workflow-id').val(workflowId);
    $('#workflow-name-display').text(workflowName || workflowId);
    $('#task-definition-modal').modal('show');
    loadTasksForWorkflow(workflowId);
    loadTaskTypes();
}

function loadTasksForWorkflow(workflowId) {
    $('#task-table tbody').html('<tr><td colspan="8" class="text-center">Loading tasks...</td></tr>');
    API.getTasksByWorkflow(workflowId)
        .done(function (r) { displayTasks(r); })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load tasks';
            $('#task-table tbody').html(`<tr><td colspan="8" class="text-center text-danger">${msg}</td></tr>`);
        });
}

function loadTaskTypes() {
    API.getTaskTypes({ pageSize: 50 })
        .done(function (r) { populateTaskTypeDropdown(r); })
        .fail(function (xhr) { console.error('Failed to load task types', xhr); });
}

function populateTaskTypeDropdown(response) {
    const $dd = $('#task-type');
    $dd.empty().append('<option value="">Select task type...</option>');

    if (response && response.records && response.records.length > 0) {
        const groups = {};
        response.records.forEach(function (tt) {
            const g = tt.typeGroup || 'Other';
            if (!groups[g]) groups[g] = [];
            groups[g].push(tt);
        });
        Object.keys(groups).sort().forEach(function (g) {
            const $og = $('<optgroup>').attr('label', g);
            groups[g].sort((a, b) => a.typeName.localeCompare(b.typeName)).forEach(function (tt) {
                $og.append($('<option>').val(tt.typeName)
                    .text(`${tt.typeName} - ${tt.typeDesc || ''}`)
                    .data('pvalue', tt.pvalue || '{}')
                    .data('description', tt.typeDesc || ''));
            });
            $dd.append($og);
        });
    }
    initTaskTypeDropdown();
}

function initTaskTypeDropdown() {
    $('#task-type').off('change').on('change', function () {
        const sel = $(this).val();
        if (sel) fetchTaskTypeParameters(sel);
    });
}

function fetchTaskTypeParameters(typeName) {
    $('#task-parameters').val('Loading reference parameters...');
    API.getTaskTypes({ typeName })
        .done(function (r) {
            if (r && r.records && r.records.length > 0) {
                $('#task-parameters').val(prettyJson(r.records[0].pvalue) || '{\n  \n}');
            } else {
                $('#task-parameters').val('{\n  \n}');
            }
        })
        .fail(function () { $('#task-parameters').val('{\n  \n}'); });
}

function displayTasks(tasks) {
    $('#task-table tbody').empty();
    if (!tasks || tasks.length === 0) {
        $('#task-table tbody').html('<tr><td colspan="8" class="text-center">No tasks found for this workflow</td></tr>');
        return;
    }
    tasks.forEach(function (task) {
        const creationDate = task.creationDate ? formatLocaleDateTime(task.creationDate) : 'N/A';
        const params       = prettyJson(task.pvalue);
        const sb = (task.enableFlag === 'Y' || task.enableFlag === true)
            ? '<span class="badge bg-success">Active</span>'
            : '<span class="badge bg-secondary">Inactive</span>';

        $('#task-table tbody').append(`
            <tr data-id="${task.taskId}">
                <td>${task.taskId}</td>
                <td>${task.taskName}</td>
                <td>${task.taskDesc || ''}</td>
                <td><pre class="m-0" style="max-width:300px;max-height:45px;overflow:auto">${params}</pre></td>
                <td><code>${task.taskType || ''}</code></td>
                <td>${creationDate}</td>
                <td>${sb}</td>
                <td>
                    ${adminBtn(`<button class="btn btn-sm btn-outline-secondary edit-task-btn me-1" data-id="${task.taskId}"><i class="bi bi-pencil"></i></button>`)}
                    ${adminBtn(`<button class="btn btn-sm btn-outline-danger delete-task-btn" data-id="${task.taskId}"><i class="bi bi-trash"></i></button>`)}
                </td>
            </tr>`);
    });

    $('.edit-task-btn').on('click', function () {
        showTaskForm($(this).data('id'), $('#current-workflow-id').val());
    });
    $('.delete-task-btn').on('click', function () {
        const id = $(this).data('id');
        showConfirmDialog({ message: `Are you sure you want to delete task ${id}?`, onConfirm: () => deleteTask(id) });
    });
}

function showTaskForm(taskId, workflowId) {
    $('#task-form')[0].reset();
    $('#task-id').val('');
    $('#task-workflow-id').val(workflowId);

    if (taskId) {
        $('#task-form-modal .modal-title').text('Edit Task');
        API.getTask(taskId)
            .done(function (task) {
                $('#task-id').val(task.taskId);
                $('#task-name').val(task.taskName);
                $('#task-description').val(task.taskDesc || '');
                $('#task-type').val(task.taskType || '');
                $('#task-parameters').val(prettyJson(task.pvalue));
                $('#task-status').val(task.enableFlag || 'A');
                $('#task-form-modal').modal('show');
            })
            .fail(function (xhr) {
                showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load task details', 'danger');
            });
    } else {
        $('#task-form-modal .modal-title').text('Create Task');
        $('#task-name').val('').prop('readonly', false).removeClass('bg-light text-muted');
        $('#task-parameters').val('{\n  "wfP1": "donot use param as key",\n  "wfP2": "value2"\n}');
        $('#task-status').val('Y');
        $('#task-form-modal').modal('show');
    }
}

function saveTask() {
    const form = $('#task-form')[0];
    if (!form.checkValidity()) { form.reportValidity(); return; }
    if (!isValidJson($('#task-parameters').val())) {
        showToast('Error', 'Invalid JSON format in Parameters', 'danger'); return;
    }

    const taskId     = $('#task-id').val();
    const workflowId = $('#task-workflow-id').val();
    const isNew      = !taskId;
    const username   = getUsername();

    const task = {
        taskName    : $('#task-name').val(),
        taskDesc    : $('#task-description').val(),
        taskType    : $('#task-type').val(),
        pvalue      : $('#task-parameters').val(),
        enableFlag  : $('#task-status').val(),
        workflowId,
        lastUpdatedBy: username
    };
    if (isNew)  { task.createdBy = username; }
    else        { task.taskId = taskId; }

    const apiData = isNew ? { workflowId, taskDefinition: task } : task;

    $('#save-task-btn').prop('disabled', true)
        .html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Saving...');

    const call = isNew ? API.createTask(apiData) : API.updateTask(apiData);
    call.done(function (r) {
        if (r && r.status === 'success') {
            $('#task-form-modal').modal('hide');
            loadTasksForWorkflow(workflowId);
            showToast('Success', `Task ${isNew ? 'created' : 'updated'} successfully`, 'success');
        } else {
            showToast('Error', `Failed to ${isNew ? 'create' : 'update'} task: ${(r && r.error) || 'Unknown error'}`, 'danger');
        }
    }).fail(function (xhr) {
        showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || `Failed to ${isNew ? 'create' : 'update'} task`, 'danger');
    }).always(function () {
        $('#save-task-btn').prop('disabled', false).text('Save');
    });
}

function deleteTask(taskId) {
    API.deleteTask(taskId)
        .done(function (r) {
            if (r && r.status === 'success') {
                loadTasksForWorkflow($('#current-workflow-id').val());
                showToast('Success', `Task ${taskId} deleted successfully`, 'success');
            } else {
                showToast('Error', `Failed to delete task: ${(r && r.error) || 'Unknown error'}`, 'danger');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to delete task', 'danger');
        });
}

// ─────────────────────────────────────────────────────────────────────────────
//  Excel Export / Import
// ─────────────────────────────────────────────────────────────────────────────

function exportWorkflowExcel() {
    const $btn = $('#export-excel-btn');
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Exporting...');

    // Pass current search filters so only matching workflows are exported
    const filterParams = {};
    const wfId   = $('#workflow-id-search').val();
    const wfName = $('#workflow-name-search').val();
    if (wfId)   filterParams.workflowId   = wfId;
    if (wfName) filterParams.workflowName = wfName;

    API.exportDefinitionExcel(filterParams)
        .then(function (res) {
            if (!res.ok) {
                return res.json().then(function (d) { throw new Error(d.error || 'Export failed (HTTP ' + res.status + ')'); });
            }
            return res.blob();
        })
        .then(function (blob) {
            const url      = URL.createObjectURL(blob);
            const link     = document.createElement('a');
            link.href      = url;
            link.download  = 'workflow_definitions.xlsx';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            setTimeout(function () { URL.revokeObjectURL(url); }, 200);
            showToast('Success', 'Workflow definitions exported successfully', 'success');
        })
        .catch(function (err) {
            showToast('Error', 'Export failed: ' + err.message, 'danger');
        })
        .finally(function () {
            $btn.prop('disabled', false).html('<i class="bi bi-file-earmark-arrow-down"></i> Export Excel');
        });
}

function importWorkflowExcel(file) {
    if (!file) return;

    // Basic file-type check
    if (!file.name.match(/\.xlsx$/i)) {
        showToast('Error', 'Please select a valid .xlsx file', 'danger');
        return;
    }

    const $btn = $('#import-excel-btn');
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Importing...');

    const formData = new FormData();
    formData.append('file', file, file.name);

    API.importDefinitionExcel(formData)
        .then(function (res) { return res.json(); })
        .then(function (data) {
            showImportResultModal(data);
            // Reload workflow list regardless of partial errors
            const cp = parseInt($('#workflow-pagination .page-item.active .page-link').data('page')) || 1;
            const { orderBy, sortDirection } = getCurrentSortFromIcons();
            loadWorkflowData(cp, 10, orderBy || 'WORKFLOW_NAME', sortDirection || 'ASC');
        })
        .catch(function (err) {
            showToast('Error', 'Import failed: ' + err.message, 'danger');
        })
        .finally(function () {
            $btn.prop('disabled', false).html('<i class="bi bi-file-earmark-arrow-up"></i> Import Excel');
        });
}

function showImportResultModal(data) {
    const errCount = data.errors ? data.errors.length : 0;
    const allOk    = errCount === 0;

    const summaryHtml = `
        <div class="row text-center mb-3">
            <div class="col-3">
                <div class="fw-bold fs-4 text-success">${data.workflowsCreated || 0}</div>
                <div class="text-muted small">Workflows Created</div>
            </div>
            <div class="col-3">
                <div class="fw-bold fs-4 text-primary">${data.workflowsUpdated || 0}</div>
                <div class="text-muted small">Workflows Updated</div>
            </div>
            <div class="col-3">
                <div class="fw-bold fs-4 text-success">${data.tasksCreated || 0}</div>
                <div class="text-muted small">Tasks Created</div>
            </div>
            <div class="col-3">
                <div class="fw-bold fs-4 text-primary">${data.tasksUpdated || 0}</div>
                <div class="text-muted small">Tasks Updated</div>
            </div>
        </div>`;

    let errHtml = '';
    if (!allOk) {
        const errItems = (data.errors || []).map(function (e) {
            return `<li class="list-group-item list-group-item-danger py-1 small">${escapeHtml(e)}</li>`;
        }).join('');
        errHtml = `
            <div class="mt-2">
                <div class="fw-semibold text-danger mb-1"><i class="bi bi-exclamation-triangle-fill"></i> Validation / Import Errors (${errCount})</div>
                <ul class="list-group" style="max-height:260px;overflow-y:auto">${errItems}</ul>
            </div>`;
    }

    const iconHtml = allOk
        ? '<i class="bi bi-check-circle-fill text-success fs-3 me-2"></i>'
        : '<i class="bi bi-exclamation-triangle-fill text-warning fs-3 me-2"></i>';
    const titleText = allOk ? 'Import Completed Successfully' : 'Import Completed with Errors';

    $('#import-result-modal-title').html(iconHtml + titleText);
    $('#import-result-modal-body').html(summaryHtml + errHtml);
    new bootstrap.Modal('#import-result-modal').show();
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
