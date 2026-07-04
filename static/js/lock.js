/**
 * lock.js - Release Lock module
 */

// ═══════════════════════════════════════════════════════════════
//  LOCK MODULE
// ═══════════════════════════════════════════════════════════════

function initLockModule() {
    $('#lock-search-btn').on('click', function () {
        loadLockData();
    });

    $('#lock-reset-btn').on('click', function () {
        $('#lock-name-search').val('');
        $('#lock-owner-search').val('');
        loadLockData();
    });

    $('#lock-release-all-btn').on('click', function () {
        const lockOwner = $('#lock-owner-search').val().trim();
        if (lockOwner) {
            releaseAllLocksByOwner(lockOwner);
        } else {
            releaseAllLocks();
        }
    });
}

function loadLockData() {
    const lockName  = $('#lock-name-search').val().trim();
    const lockOwner = $('#lock-owner-search').val().trim();

    $('#lock-table tbody').html(
        '<tr><td colspan="5" class="text-center"><span class="spinner-border spinner-border-sm me-2"></span>Loading locks...</td></tr>'
    );

    const params = {};
    if (lockName)  params.name  = lockName;
    if (lockOwner) params.owner = lockOwner;

    API.getLocks(params)
        .done(function (locks) {
            displayLocks(locks);
        })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load locks';
            $('#lock-table tbody').html(
                `<tr><td colspan="5" class="text-center text-danger"><i class="bi bi-exclamation-circle me-1"></i>${msg}</td></tr>`
            );
        });
}

function displayLocks(locks) {
    const $tbody = $('#lock-table tbody');
    $tbody.empty();

    if (!locks || locks.length === 0) {
        $tbody.html('<tr><td colspan="5" class="text-center text-muted">No locks found</td></tr>');
        $('#lock-count-badge').text('0');
        return;
    }

    $('#lock-count-badge').text(locks.length);

    locks.forEach(function (lock) {
        const lockDate = lock.lockDate ? formatLocaleDateTime(lock.lockDate) : 'N/A';
        const lockTypeBadge = lock.lockType === 'W'
            ? '<span class="badge bg-danger">WRITE</span>'
            : '<span class="badge bg-warning text-dark">READ</span>';

        $tbody.append(`
            <tr>
                <td><code>${escapeHtml(lock.lockName || '')}</code></td>
                <td>${escapeHtml(lock.lockOwner || 'N/A')}</td>
                <td>${lockTypeBadge}</td>
                <td>${lockDate}</td>
                <td>
                    <button class="btn btn-sm btn-outline-danger release-lock-btn"
                            data-lock-name="${escapeAttr(lock.lockName)}"
                            title="Release this lock">
                        <i class="bi bi-unlock"></i> Release Lock
                    </button>
                </td>
            </tr>`);
    });

    // Attach handlers after rendering
    $('.release-lock-btn').on('click', function () {
        const lockName = $(this).data('lock-name');
        releaseLock(lockName);
    });
}

function releaseLock(lockName) {
    if (!lockName) return;

    showConfirmDialog({
        title: 'Release Lock',
        message: `Are you sure you want to release lock:<br><code>${escapeHtml(lockName)}</code>`,
        okButtonClass: 'btn-danger',
        onConfirm: function () {
            const toast = showToast('Processing', `Releasing lock "${lockName}"...`, 'info', { autohide: false });

            API.releaseLock(lockName)
                .done(function (r) {
                    toast.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `Lock "${lockName}" released successfully`, 'success');
                        loadLockData();
                    } else {
                        showToast('Error', `Failed to release lock: ${(r && r.error) || 'Unknown error'}`, 'danger');
                    }
                })
                .fail(function (xhr) {
                    toast.hide();
                    const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to release lock';
                    showToast('Error', msg, 'danger');
                });
        }
    });
}

function releaseAllLocks() {
    showConfirmDialog({
        title: 'Release All Locks',
        message: 'Are you sure you want to release <strong>ALL</strong> locks? This action cannot be undone.',
        okButtonClass: 'btn-danger',
        onConfirm: function () {
            const toast = showToast('Processing', 'Releasing all locks...', 'info', { autohide: false });

            API.releaseAllLocks()
                .done(function (r) {
                    toast.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `All locks released (${r.count || 0} total)`, 'success');
                        loadLockData();
                    } else {
                        showToast('Error', `Failed to release all locks: ${(r && r.error) || 'Unknown error'}`, 'danger');
                    }
                })
                .fail(function (xhr) {
                    toast.hide();
                    const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to release all locks';
                    showToast('Error', msg, 'danger');
                });
        }
    });
}

function releaseAllLocksByOwner(lockOwner) {
    showConfirmDialog({
        title: 'Release Locks by Owner',
        message: `Are you sure you want to release all locks owned by:<br><code>${escapeHtml(lockOwner)}</code>`,
        okButtonClass: 'btn-danger',
        onConfirm: function () {
            const toast = showToast('Processing', `Releasing locks for owner "${lockOwner}"...`, 'info', { autohide: false });

            API.releaseAllLocksByOwner(lockOwner)
                .done(function (r) {
                    toast.hide();
                    if (r && r.status === 'success') {
                        showToast('Success', `Released ${r.count || 0} lock(s) for owner "${lockOwner}"`, 'success');
                        loadLockData();
                    } else {
                        showToast('Error', `Failed to release locks: ${(r && r.error) || 'Unknown error'}`, 'danger');
                    }
                })
                .fail(function (xhr) {
                    toast.hide();
                    const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to release locks';
                    showToast('Error', msg, 'danger');
                });
        }
    });
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function escapeAttr(str) {
    if (str == null) return '';
    return String(str).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}
