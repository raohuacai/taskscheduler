/**
 * tasktype.js - Task Type module & Global Params module
 */

// ═══════════════════════════════════════════════════════════════
//  TASK TYPE MODULE
// ═══════════════════════════════════════════════════════════════

function initTaskTypeModule() {
    let currentPage   = 1;
    let pageSize      = 10;
    let orderBy       = 'TYPE_GROUP';
    let sortDirection = 'ASC';

    $('#add-tasktype-btn').on('click', function () {
        $('#tasktype-form-modal .modal-title').text('Create Task Type');
        $('#tasktype-form')[0].reset();
        $('#tasktype-id').val('');
        $('#tasktype-name').prop('readonly', false).removeClass('bg-light text-muted');
        $('#tasktype-interruptable').val('Y');
        $('#tasktype-params').val('{\n  "wfP1": "donot use param as key",\n  "wfP2": "value2"\n}');
        $('#tasktype-form-modal').modal('show');
    });

    $('#save-tasktype-btn').on('click', function () { saveTaskType(); });

    $('#tasktype-search-btn').on('click', function () {
        currentPage = 1;
        loadTaskTypeData(currentPage, pageSize, orderBy, sortDirection);
    });

    $('#tasktype-reset-btn').on('click', function () {
        $('#tasktype-group-search').val('');
        $('#tasktype-name-search').val('');
        currentPage = 1; orderBy = 'TYPE_NAME'; sortDirection = 'ASC';
        loadTaskTypeData(currentPage, pageSize, orderBy, sortDirection);
        updateSortIcons(orderBy, sortDirection);
    });

    $('#tasktype-pagination').on('click', '.page-link', function (e) {
        e.preventDefault();
        const p = $(this).data('page');
        if (p) { currentPage = p; loadTaskTypeData(currentPage, pageSize, orderBy, sortDirection); }
    });

    $('#tasktype-table').on('click', 'th .th-content, th .sort-icon', function () {
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
        loadTaskTypeData(currentPage, pageSize, orderBy, sortDirection);
    });
}

function loadTaskTypeData(pageNum, pageSize, orderBy, sortDirection) {
    pageNum       = pageNum       || 1;
    pageSize      = pageSize      || 10;
    orderBy       = orderBy       || 'TYPE_NAME';
    sortDirection = sortDirection || 'ASC';

    const typeGroup = $('#tasktype-group-search').val();
    const typeName  = $('#tasktype-name-search').val();

    const params = { pageNum, pageSize, orderBy: orderBy + ' ' + sortDirection };
    if (typeGroup) params.typeGroup = typeGroup;
    if (typeName)  params.typeName  = typeName;

    $('#tasktype-table tbody').html('<tr><td colspan="7" class="text-center">Loading...</td></tr>');

    API.getTaskTypes(params)
        .done(function (r) {
            displayTaskTypes(r);
            buildPagination(r, pageNum, 'tasktype-pagination');
            updateSortIcons(orderBy, sortDirection);
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load task types';
            $('#tasktype-table tbody').html(`<tr><td colspan="7" class="text-center text-danger">${msg}</td></tr>`);
            $('#tasktype-pagination').empty();
        });
}

function displayTaskTypes(response) {
    $('#tasktype-table tbody').empty();
    const taskTypes = response.records;
    if (!taskTypes || taskTypes.length === 0) {
        $('#tasktype-table tbody').html('<tr><td colspan="7" class="text-center">No task types found</td></tr>');
        return;
    }

    taskTypes.forEach(function (type) {
        const creationDate = type.creationDate ? formatLocaleDateTime(type.creationDate) : 'N/A';
        const refParams    = prettyJson(type.pvalue);

        $('#tasktype-table tbody').append(`
            <tr data-id="${type.typeName}">
                <td>${type.typeGroup || ''}</td>
                <td>${type.typeName}</td>
                <td><code>${type.className || ''}</code></td>
                <td>${type.typeDesc || ''}</td>
                <td><pre class="m-0" style="max-width:450px;max-height:45px;overflow:auto">${refParams}</pre></td>
                <td>${creationDate}</td>
                <td>
                    ${adminBtn(`<button class="btn btn-sm btn-outline-secondary edit-tasktype-btn" data-id="${type.typeName}"><i class="bi bi-pencil"></i></button>`)}
                    ${adminBtn(`<button class="btn btn-sm btn-outline-danger delete-tasktype-btn" data-id="${type.typeName}"><i class="bi bi-trash"></i></button>`)}
                </td>
            </tr>`);
    });

    $('.edit-tasktype-btn').on('click', function () { editTaskType($(this).data('id')); });
    $('.delete-tasktype-btn').on('click', function () {
        const id = $(this).data('id');
        showConfirmDialog({
            message  : `Are you sure you want to delete task type ${id}?`,
            onConfirm: () => deleteTaskType(id)
        });
    });
}

function saveTaskType() {
    const form = $('#tasktype-form')[0];
    if (!form.checkValidity()) { form.reportValidity(); return; }
    if (!isValidJson($('#tasktype-params').val())) {
        showToast('Error', 'Invalid JSON format in Reference Parameters', 'danger'); return;
    }

    const typeName = $('#tasktype-name').val();
    const isNew    = !$('#tasktype-id').val();
    const username = getUsername();

    const tt = {
        typeName        : typeName,
        typeGroup       : $('#tasktype-group').val(),
        className       : $('#tasktype-class').val(),
        typeDesc        : $('#tasktype-description').val(),
        pvalue          : $('#tasktype-params').val(),
        interruptableFlag: $('#tasktype-interruptable').val(),
        lastUpdatedBy   : username
    };

    $('#save-tasktype-btn').prop('disabled', true)
        .html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Saving...');

    const call = isNew ? API.createTaskType(tt) : API.updateTaskType(tt);
    call.done(function (r) {
        if (r && r.status === 'success') {
            $('#tasktype-form-modal').modal('hide');
            const cp = parseInt($('#tasktype-pagination .page-item.active .page-link').data('page')) || 1;
            const { orderBy, sortDirection } = getCurrentSortFromIcons();
            loadTaskTypeData(cp, 10, orderBy || 'TYPE_NAME', sortDirection || 'ASC');
            showToast('Success', `Task type ${isNew ? 'created' : 'updated'} successfully`, 'success');
        } else {
            showToast('Error', `Failed to ${isNew ? 'create' : 'update'} task type: ${(r && r.error) || 'Unknown error'}`, 'danger');
        }
    }).fail(function (xhr) {
        showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || `Failed to ${isNew ? 'create' : 'update'} task type`, 'danger');
    }).always(function () {
        $('#save-tasktype-btn').prop('disabled', false).text('Save');
    });
}

function editTaskType(typeName) {
    $('#tasktype-form-modal .modal-title').text('Edit Task Type (Loading...)');
    $('#tasktype-form-modal').modal('show');

    API.getTaskTypes({ typeName })
        .done(function (r) {
            $('#tasktype-form-modal .modal-title').text('Edit Task Type');
            if (r && r.records && r.records.length > 0) {
                const tt = r.records[0];
                $('#tasktype-id').val(tt.typeName);
                $('#tasktype-name').val(tt.typeName).prop('readonly', true).addClass('bg-light text-muted');
                $('#tasktype-group').val(tt.typeGroup || '');
                $('#tasktype-class').val(tt.className || '');
                $('#tasktype-description').val(tt.typeDesc || '');
                $('#tasktype-params').val(prettyJson(tt.pvalue));
                $('#tasktype-interruptable').val(tt.interruptableFlag || 'Y');
            } else {
                showToast('Error', 'Task type not found', 'danger');
                $('#tasktype-form-modal').modal('hide');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load task type details', 'danger');
            $('#tasktype-form-modal').modal('hide');
        });
}

function deleteTaskType(typeName) {
    API.deleteTaskType(typeName, getUsername())
        .done(function (r) {
            if (r && r.status === 'success') {
                const cp = parseInt($('#tasktype-pagination .page-item.active .page-link').data('page')) || 1;
                const { orderBy, sortDirection } = getCurrentSortFromIcons();
                loadTaskTypeData(cp, 10, orderBy || 'TYPE_NAME', sortDirection || 'ASC');
                showToast('Success', `Task type ${typeName} deleted successfully`, 'success');
            } else {
                showToast('Error', `Failed to delete task type: ${(r && r.error) || 'Unknown error'}`, 'danger');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to delete task type', 'danger');
        });
}

// ═══════════════════════════════════════════════════════════════
//  GLOBAL PARAMS MODULE
// ═══════════════════════════════════════════════════════════════

function initGlobalParamsModule() {
    $('#global-params-table').on('click', '.update-param-btn', function () {
        saveGlobalParam($(this).data('pname'));
    });
}

function loadGlobalParams() {
    $('#global-params-table tbody').html('<tr><td colspan="3" class="text-center">Loading parameters...</td></tr>');

    API.getGlobalParams()
        .done(function (r) { displayGlobalParams(r); })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load global parameters';
            $('#global-params-table tbody').html(`<tr><td colspan="3" class="text-center text-danger">${msg}</td></tr>`);
        });
}

function displayGlobalParams(params) {
    $('#global-params-table tbody').empty();
    if (!params || params.length === 0) {
        $('#global-params-table tbody').html('<tr><td colspan="3" class="text-center">No parameters found</td></tr>');
        return;
    }

    const supported = ['WfGlobalParams','ThreadPoolConfig','EmailConfig','EmailTemplateConfig','HttpServerConfig'];

    params.forEach(function (param) {
        if (!supported.includes(param.pname)) return;

        let formattedValue = param.pvalue || '';
        let valueDisplay;
        try {
            if (formattedValue) {
                formattedValue = prettyJson(formattedValue);
                valueDisplay = `<textarea class="form-control json-editor" rows="${Math.min(20, formattedValue.split('\n').length)}">${formattedValue}</textarea>`;
            } else {
                valueDisplay = '<textarea class="form-control json-editor" rows="3"></textarea>';
            }
        } catch (e) {
            valueDisplay = `<textarea class="form-control" rows="3">${formattedValue}</textarea>`;
        }

        $('#global-params-table tbody').append(`
            <tr data-pname="${param.pname}">
                <td><strong>${param.pname}</strong></td>
                <td>${valueDisplay}</td>
                <td class="text-center">
                    ${adminBtn(`<button class="btn btn-sm btn-outline-primary update-param-btn" data-pname="${param.pname}">
                        <i class="bi bi-save"></i> Update
                    </button>`)}
                </td>
            </tr>`);
    });
}

function saveGlobalParam(paramName) {
    const $row      = $(`#global-params-table tbody tr[data-pname="${paramName}"]`);
    const $textarea = $row.find('textarea');
    const paramValue = $textarea.val();

    if (!isValidJson(paramValue)) {
        $textarea.addClass('is-invalid');
        if (!$row.next('.error-message').length) {
            try { JSON.parse(paramValue); } catch (e) {
                $row.after(`<tr class="error-message"><td colspan="3" class="text-danger">Invalid JSON format: ${e.message}</td></tr>`);
            }
        }
        return;
    }

    $textarea.removeClass('is-invalid');
    $row.next('.error-message').remove();

    let jsonValue = {};
    try {
        jsonValue = JSON.parse(paramValue);
        jsonValue.lastUpdatedBy = getUsername();
    } catch (e) { return; }

    const $btn = $row.find('.update-param-btn');
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>');

    API.updateGlobalParam(paramName, jsonValue)
        .done(function (r) {
            if (r && r.status === 'success') {
                showToast('Success', `${paramName} updated successfully`, 'success');
            } else {
                showToast('Error', `Failed to update ${paramName}: ${(r && r.error) || 'Unknown error'}`, 'danger');
            }
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || `Failed to update ${paramName}`;
            showToast('Error', msg, 'danger');
            if (xhr.status === 401) {
                setTimeout(function () {
                    localStorage.removeItem('auth_token');
                    localStorage.removeItem('username');
                    window.location.href = 'login.html';
                }, 2000);
            }
        })
        .always(function () {
            $btn.prop('disabled', false).html('<i class="bi bi-save"></i> Update');
        });
}
