/**
 * scheduler.js - Scheduler module
 */

function initSchedulerModule() {
    let currentPage   = 1;
    let pageSize      = 10;
    let orderBy       = 'WORKFLOW_NAME';
    let sortDirection = 'ASC';

    $('#add-scheduler-btn').on('click', function () {
        $('#scheduler-form-modal .modal-title').text('Create Schedule');
        $('#scheduler-form')[0].reset();
        $('#scheduler-id').val('');
        $('#scheduler-workflow-id').prop('readonly', false).removeClass('bg-light text-muted');
        $('#scheduler-enable-flag').val('true');
        $('#scheduler-form-modal').modal('show');
    });

    $('#save-scheduler-btn').on('click', function () { saveScheduler(); });

    $('#scheduler-search-btn').on('click', function () {
        currentPage = 1;
        loadSchedulerData(currentPage, pageSize, orderBy, sortDirection);
    });

    $('#scheduler-reset-btn').on('click', function () {
        $('#scheduler-workflow-id-search').val('');
        $('#scheduler-workflow-name-search').val('');
        currentPage = 1; orderBy = 'WORKFLOW_NAME'; sortDirection = 'ASC';
        loadSchedulerData(currentPage, pageSize, orderBy, sortDirection);
        updateSortIcons(orderBy, sortDirection, 'scheduler-');
    });

    $('#trigger-workflow-btn').on('click', function () {
        const workflowId   = $('#trigger-workflow-id').val();
        const parametersStr = $('#trigger-parameters').val();
        const username     = getUsername();

        let parameters = {};
        if (parametersStr && parametersStr.trim()) {
            try {
                parameters = JSON.parse(parametersStr);
                $('#trigger-parameters').removeClass('is-invalid');
            } catch (e) {
                $('#trigger-parameters').addClass('is-invalid');
                return;
            }
        }

        $('#trigger-workflow-btn').prop('disabled', true)
            .html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Triggering...');

        API.startWorkflow({ workflowId, parameters, triggeredBy: username })
            .done(function (r) {
                $('#workflow-trigger-modal').modal('hide');
                if (r.status === 'success') {
                    showToast('Success', `Workflow started. Instance ID: ${r.instanceId}`, 'success');
                } else {
                    showToast('Error', `Failed to start workflow: ${r.error || 'Unknown error'}`, 'danger');
                }
            })
            .fail(function (xhr) {
                showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to start workflow', 'danger');
            })
            .always(function () {
                $('#trigger-workflow-btn').prop('disabled', false).text('Trigger');
            });
    });

    $('#scheduler-pagination').on('click', '.page-link', function (e) {
        e.preventDefault();
        const p = $(this).data('page');
        if (p) { currentPage = p; loadSchedulerData(currentPage, pageSize, orderBy, sortDirection); }
    });

    $('#scheduler-table').on('click', 'th .th-content, th .sort-icon', function () {
        let column = $(this).hasClass('th-content')
            ? $(this).data('column')
            : $(this).prev('.th-content').data('column');

        if (orderBy === column) {
            sortDirection = sortDirection === 'ASC' ? 'DESC' : 'ASC';
        } else {
            orderBy = column; sortDirection = 'ASC';
        }
        updateSortIcons(orderBy, sortDirection, 'scheduler-');
        currentPage = 1;
        loadSchedulerData(currentPage, pageSize, orderBy, sortDirection);
    });
}

function loadSchedulerData(pageNum, pageSize, orderBy, sortDirection) {
    pageNum       = pageNum       || 1;
    pageSize      = pageSize      || 10;
    orderBy       = orderBy       || 'WORKFLOW_NAME';
    sortDirection = sortDirection || 'ASC';

    const workflowId   = $('#scheduler-workflow-id-search').val();
    const workflowName = $('#scheduler-workflow-name-search').val();

    const params = { pageNum, pageSize, orderBy: orderBy + ' ' + sortDirection };
    if (workflowId)   params.workflowId   = workflowId;
    if (workflowName) params.workflowName = workflowName;

    $('#scheduler-table tbody').html('<tr><td colspan="7" class="text-center">Loading...</td></tr>');

    API.getSchedulers(params)
        .done(function (r) {
            displaySchedulers(r);
            buildPagination(r, pageNum, 'scheduler-pagination');
            updateSortIcons(orderBy, sortDirection, 'scheduler-');
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load schedulers';
            $('#scheduler-table tbody').html(`<tr><td colspan="7" class="text-center text-danger">${msg}</td></tr>`);
            $('#scheduler-pagination').empty();
        });
}

function displaySchedulers(response) {
    $('#scheduler-table tbody').empty();
    const schedulers = response.records;
    if (!schedulers || schedulers.length === 0) {
        $('#scheduler-table tbody').html('<tr><td colspan="7" class="text-center">No schedulers found</td></tr>');
        return;
    }

    schedulers.forEach(function (sc) {
        const creationDate = sc.creationDate ? formatLocaleDateTime(sc.creationDate) : 'N/A';

        let nextRunTime = 'N/A';
        if (sc.nextRunTime) {
            nextRunTime = formatLocaleDateTime(sc.nextRunTime);
        } else if (sc.enableFlag === 'Y' || sc.enableFlag === true) {
            nextRunTime = '<span class="text-warning">Calculation error</span>';
        } else {
            nextRunTime = '<span class="text-muted">Disabled</span>';
        }

        let isEnabled = false;
        if (typeof sc.enableFlag === 'boolean') { isEnabled = sc.enableFlag; }
        else if (sc.enableFlag === 'Y')          { isEnabled = true; }

        const statusBadge = isEnabled
            ? '<span class="badge bg-success">Enabled</span>'
            : '<span class="badge bg-secondary">Disabled</span>';

        $('#scheduler-table tbody').append(`
            <tr data-id="${sc.workflowId}">
                <td>${sc.workflowId}</td>
                <td>${sc.workflowName || 'N/A'}</td>
                <td><code>${sc.cron || sc.cronExpression || 'N/A'}</code></td>
                <td>${nextRunTime}</td>
                <td>${statusBadge}</td>
                <td>${creationDate}</td>
                <td>
                    <button class="btn btn-sm btn-outline-primary trigger-workflow-btn me-1" data-id="${sc.workflowId}" data-name="${sc.workflowName || ''}">
                        <i class="bi bi-play-fill"></i> Trigger
                    </button>
                    <button class="btn btn-sm btn-outline-secondary edit-scheduler-btn" data-id="${sc.workflowId}">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger delete-scheduler-btn" data-id="${sc.workflowId}">
                        <i class="bi bi-trash"></i>
                    </button>
                </td>
            </tr>`);
    });

    $('.edit-scheduler-btn').on('click', function () { editScheduler($(this).data('id')); });
    $('.delete-scheduler-btn').on('click', function () {
        const id = $(this).data('id');
        showConfirmDialog({
            message  : `Are you sure you want to delete schedule for workflow ${id}?`,
            onConfirm: () => deleteScheduler(id)
        });
    });
    $('.trigger-workflow-btn').on('click', function () {
        triggerWorkflow($(this).data('id'), $(this).data('name'));
    });
}

function saveScheduler() {
    const form = $('#scheduler-form')[0];
    if (!form.checkValidity()) { form.reportValidity(); return; }

    const workflowId = $('#scheduler-workflow-id').val();
    const isNew      = !$('#scheduler-id').val();
    const username   = getUsername();

    const sc = {
        workflowId,
        cronExpression: $('#scheduler-cron-expression').val(),
        enableFlag    : $('#scheduler-enable-flag').val() === 'true' ? 'Y' : 'N',
        lastUpdatedBy : username
    };
    if (isNew) sc.createdBy = username;

    $('#save-scheduler-btn').prop('disabled', true)
        .html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Saving...');

    const call = isNew ? API.createScheduler(sc) : API.updateScheduler(sc);
    call.done(function (r) {
        if (r && r.status === 'success') {
            $('#scheduler-form-modal').modal('hide');
            const cp = parseInt($('#scheduler-pagination .page-item.active .page-link').data('page')) || 1;
            const { orderBy, sortDirection } = getCurrentSortFromIcons('scheduler-');
            loadSchedulerData(cp, 10, orderBy || 'WORKFLOW_NAME', sortDirection || 'ASC');
            showToast('Success', `Schedule ${isNew ? 'created' : 'updated'} successfully`, 'success');
        } else {
            showToast('Error', `Failed to ${isNew ? 'create' : 'update'} schedule: ${(r && r.error) || 'Unknown error'}`, 'danger');
        }
    }).fail(function (xhr) {
        showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || `Failed to ${isNew ? 'create' : 'update'} schedule`, 'danger');
    }).always(function () {
        $('#save-scheduler-btn').prop('disabled', false).text('Save');
    });
}

function editScheduler(workflowId) {
    $('#scheduler-form-modal .modal-title').text('Edit Schedule (Loading...)');
    $('#scheduler-form-modal').modal('show');

    API.getSchedulerById(workflowId)
        .done(function (sc) {
            $('#scheduler-form-modal .modal-title').text('Edit Schedule');
            if (sc && sc.workflowId) {
                $('#scheduler-id').val(sc.workflowId);
                $('#scheduler-workflow-id').val(sc.workflowId).prop('readonly', true).addClass('bg-light text-muted');
                $('#scheduler-cron-expression').val(sc.cron || sc.cronExpression);
                let enableValue = 'false';
                if (typeof sc.enableFlag === 'boolean') { enableValue = sc.enableFlag ? 'true' : 'false'; }
                else if (sc.enableFlag === 'Y')          { enableValue = 'true'; }
                $('#scheduler-enable-flag').val(enableValue);
            } else {
                showToast('Error', 'Schedule not found', 'danger');
                $('#scheduler-form-modal').modal('hide');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load schedule details', 'danger');
            $('#scheduler-form-modal').modal('hide');
        });
}

function deleteScheduler(workflowId) {
    API.deleteScheduler(workflowId)
        .done(function (r) {
            if (r && r.status === 'success') {
                const cp = parseInt($('#scheduler-pagination .page-item.active .page-link').data('page')) || 1;
                const { orderBy, sortDirection } = getCurrentSortFromIcons('scheduler-');
                loadSchedulerData(cp, 10, orderBy || 'WORKFLOW_NAME', sortDirection || 'ASC');
                showToast('Success', `Schedule for workflow ${workflowId} deleted successfully`, 'success');
            } else {
                showToast('Error', `Failed to delete schedule: ${(r && r.error) || 'Unknown error'}`, 'danger');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to delete schedule', 'danger');
        });
}

function triggerWorkflow(workflowId, workflowName) {
    $('#trigger-workflow-id').val(workflowId);
    $('#trigger-workflow-name').val(workflowName);
    $('#trigger-parameters').val('').removeClass('is-invalid');
    $('#workflow-trigger-modal').modal('show');
}
