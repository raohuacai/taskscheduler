/**
 * users.js - Manage Users module
 * Visible and fully editable only for ADMIN role.
 */

// ═══════════════════════════════════════════════════════════════
//  MANAGE USERS MODULE
// ═══════════════════════════════════════════════════════════════

function initUsersModule() {
    $('#users-search-btn').on('click', function () { loadUsersData(); });

    $('#users-reset-btn').on('click', function () {
        $('#users-search-username').val('');
        $('#users-search-status').val('');
        $('#users-search-role').val('');
        loadUsersData();
    });

    $('#add-user-btn').on('click', function () { openUserForm(null); });

    $('#save-user-btn').on('click', function () { saveUser(); });

    $('#save-user-pwd-btn').on('click', function () { saveUserPassword(); });

    // Change own password (available to all roles)
    $('#change-own-pwd-btn').on('click', function () {
        $('#change-pwd-form')[0].reset();
        new bootstrap.Modal('#change-pwd-modal').show();
    });
    $('#save-own-pwd-btn').on('click', function () { saveOwnPassword(); });
}

// ─── Load & display ────────────────────────────────────────────────────────────

function loadUsersData() {
    const filterUsername = $('#users-search-username').val().toLowerCase();
    const filterStatus   = $('#users-search-status').val();
    const filterRole     = $('#users-search-role').val();

    $('#users-table tbody').html(
        '<tr><td colspan="8" class="text-center"><span class="spinner-border spinner-border-sm me-2"></span>Loading...</td></tr>'
    );

    API.getUsers()
        .done(function (users) {
            // Client-side filter
            let list = users || [];
            if (filterUsername) list = list.filter(u => (u.username || '').toLowerCase().includes(filterUsername) || (u.fullName || '').toLowerCase().includes(filterUsername));
            if (filterStatus)   list = list.filter(u => u.status === filterStatus);
            if (filterRole)     list = list.filter(u => (u.role || '').toUpperCase() === filterRole.toUpperCase());
            displayUsers(list);
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load users';
            $('#users-table tbody').html(`<tr><td colspan="8" class="text-center text-danger">${msg}</td></tr>`);
        });
}

function displayUsers(users) {
    const $tbody = $('#users-table tbody');
    $tbody.empty();

    if (!users || users.length === 0) {
        $tbody.html('<tr><td colspan="8" class="text-center text-muted">No users found</td></tr>');
        return;
    }

    const currentUser = getUsername();

    users.forEach(function (u) {
        const statusBadge = u.status === 'A'
            ? '<span class="badge bg-success">Active</span>'
            : '<span class="badge bg-secondary">Inactive</span>';
        const roleBadge = (u.role || '').toUpperCase() === 'ADMIN'
            ? '<span class="badge bg-danger">ADMIN</span>'
            : '<span class="badge bg-primary">USER</span>';
        const creationDate = u.creationDate ? formatLocaleDateTime(u.creationDate) : 'N/A';

        // Action buttons – only rendered for ADMIN role
        const actionBtns = adminBtn(`
            <button class="btn btn-sm btn-outline-secondary edit-user-btn me-1" data-username="${escapeAttr(u.username)}" title="Edit">
                <i class="bi bi-pencil"></i>
            </button>
            <button class="btn btn-sm btn-outline-warning reset-pwd-btn me-1" data-username="${escapeAttr(u.username)}" title="Reset Password">
                <i class="bi bi-key"></i>
            </button>
            ${u.username !== currentUser ? `
            <button class="btn btn-sm btn-outline-danger delete-user-btn" data-username="${escapeAttr(u.username)}" title="Delete">
                <i class="bi bi-trash"></i>
            </button>` : ''}`);

        $tbody.append(`
            <tr>
                <td><code>${escapeHtml(u.username)}</code></td>
                <td>${escapeHtml(u.fullName || '')}</td>
                <td>${escapeHtml(u.email || '')}</td>
                <td>${escapeHtml(u.phone || '')}</td>
                <td>${statusBadge}</td>
                <td>${roleBadge}</td>
                <td>${creationDate}</td>
                <td>${actionBtns || '<span class="text-muted small">—</span>'}</td>
            </tr>`);
    });

    // Attach handlers
    $('.edit-user-btn').on('click', function () { openUserForm($(this).data('username')); });
    $('.reset-pwd-btn').on('click', function () { openResetPasswordModal($(this).data('username')); });
    $('.delete-user-btn').on('click', function () {
        const username = $(this).data('username');
        showConfirmDialog({
            title: 'Delete User',
            message: `Are you sure you want to delete user <strong>${escapeHtml(username)}</strong>?`,
            okButtonClass: 'btn-danger',
            onConfirm: function () { deleteUser(username); }
        });
    });
}

// ─── Create / Edit form ────────────────────────────────────────────────────────

function openUserForm(username) {
    $('#user-form')[0].reset();
    $('#user-form-username').val('').prop('readonly', false).removeClass('bg-light text-muted');

    if (username) {
        // Edit mode
        $('#user-form-modal .modal-title').text('Edit User');
        $('#user-form-username').val(username).prop('readonly', true).addClass('bg-light text-muted');
        $('#user-form-password-group').hide(); // password not changed here

        API.getUsers()
            .done(function (users) {
                const u = (users || []).find(x => x.username === username);
                if (u) {
                    $('#user-form-fullname').val(u.fullName || '');
                    $('#user-form-email').val(u.email || '');
                    $('#user-form-phone').val(u.phone || '');
                    $('#user-form-status').val(u.status || 'A');
                    $('#user-form-role').val((u.role || 'USER').toUpperCase());
                }
            });
    } else {
        // Create mode
        $('#user-form-modal .modal-title').text('Create User');
        $('#user-form-password-group').show();
        $('#user-form-status').val('A');
        $('#user-form-role').val('USER');
    }

    new bootstrap.Modal('#user-form-modal').show();
}

function saveUser() {
    const username = $('#user-form-username').val().trim();
    const fullName = $('#user-form-fullname').val().trim();
    const email    = $('#user-form-email').val().trim();
    const phone    = $('#user-form-phone').val().trim();
    const status   = $('#user-form-status').val();
    const role     = $('#user-form-role').val();
    const password = $('#user-form-password').val();
    const isNew    = !$('#user-form-username').prop('readonly') || !$('#user-form-username').hasClass('bg-light');

    if (!username) { showToast('Error', 'Username is required', 'danger'); return; }

    const $btn = $('#save-user-btn');
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm me-1"></span>Saving...');

    const payload = { username, fullName, email, phone, status, role };
    if (isNew) payload.password = password;

    const call = isNew ? API.createUser(payload) : API.updateUser(payload);
    call.done(function (r) {
        if (r && r.status === 'success') {
            bootstrap.Modal.getInstance('#user-form-modal').hide();
            showToast('Success', `User ${isNew ? 'created' : 'updated'} successfully`, 'success');
            loadUsersData();
        } else {
            showToast('Error', (r && r.error) || 'Save failed', 'danger');
        }
    }).fail(function (xhr) {
        showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Save failed', 'danger');
    }).always(function () {
        $btn.prop('disabled', false).text('Save');
    });
}

// ─── Delete ────────────────────────────────────────────────────────────────────

function deleteUser(username) {
    API.deleteUser(username)
        .done(function (r) {
            if (r && r.status === 'success') {
                showToast('Success', `User ${username} deleted`, 'success');
                loadUsersData();
            } else {
                showToast('Error', (r && r.error) || 'Delete failed', 'danger');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Delete failed', 'danger');
        });
}

// ─── Admin reset password ──────────────────────────────────────────────────────

function openResetPasswordModal(username) {
    $('#reset-pwd-username').val(username);
    $('#reset-pwd-form')[0].reset();
    $('#reset-pwd-for-label').text(username);
    new bootstrap.Modal('#reset-pwd-modal').show();
}

function saveUserPassword() {
    const username    = $('#reset-pwd-username').val();
    const newPassword = $('#reset-pwd-new').val();
    const confirm     = $('#reset-pwd-confirm').val();

    if (!newPassword) { showToast('Error', 'New password is required', 'danger'); return; }
    if (newPassword !== confirm) { showToast('Error', 'Passwords do not match', 'danger'); return; }

    const $btn = $('#save-user-pwd-btn');
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm me-1"></span>Saving...');

    API.resetUserPassword({ username, newPassword })
        .done(function (r) {
            if (r && r.status === 'success') {
                bootstrap.Modal.getInstance('#reset-pwd-modal').hide();
                showToast('Success', `Password reset for ${username}`, 'success');
            } else {
                showToast('Error', (r && r.error) || 'Reset failed', 'danger');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Reset failed', 'danger');
        })
        .always(function () { $btn.prop('disabled', false).text('Save'); });
}

// ─── User changes own password ─────────────────────────────────────────────────

function saveOwnPassword() {
    const oldPassword = $('#own-pwd-old').val();
    const newPassword = $('#own-pwd-new').val();
    const confirm     = $('#own-pwd-confirm').val();

    if (!oldPassword || !newPassword) { showToast('Error', 'All fields are required', 'danger'); return; }
    if (newPassword !== confirm)       { showToast('Error', 'New passwords do not match', 'danger'); return; }

    const $btn = $('#save-own-pwd-btn');
    $btn.prop('disabled', true).html('<span class="spinner-border spinner-border-sm me-1"></span>Saving...');

    API.changeOwnPassword({ oldPassword, newPassword })
        .done(function (r) {
            if (r && r.status === 'success') {
                bootstrap.Modal.getInstance('#change-pwd-modal').hide();
                showToast('Success', 'Password changed successfully', 'success');
            } else {
                showToast('Error', (r && r.error) || 'Change failed', 'danger');
            }
        })
        .fail(function (xhr) {
            showToast('Error', (xhr.responseJSON && xhr.responseJSON.error) || 'Change failed', 'danger');
        })
        .always(function () { $btn.prop('disabled', false).text('Save'); });
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

function escapeHtml(str) {
    if (str == null) return '';
    return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function escapeAttr(str) {
    if (str == null) return '';
    return String(str).replace(/"/g,'&quot;').replace(/'/g,'&#39;');
}
