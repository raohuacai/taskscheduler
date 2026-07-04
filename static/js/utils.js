/**
 * utils.js - Shared utility functions
 * Task Scheduler Web UI
 */

// ─── Date Formatting ───────────────────────────────────────────────────────────

function getLocalToday() {
    const d = new Date();
    return d.getFullYear() + '-' +
           String(d.getMonth() + 1).padStart(2, '0') + '-' +
           String(d.getDate()).padStart(2, '0');
}

function formatDateTime(timestamp) {
    if (!timestamp) return 'N/A';
    const date = new Date(timestamp);
    if (isNaN(date.getTime())) return 'N/A';
    const year  = date.getFullYear();
    const month = date.getMonth() + 1;
    const day   = date.getDate();
    const h = date.getHours().toString().padStart(2, '0');
    const m = date.getMinutes().toString().padStart(2, '0');
    const s = date.getSeconds().toString().padStart(2, '0');
    return `${year}-${month}-${day} ${h}:${m}:${s}`;
}

function formatLocaleDateTime(timestamp) {
    if (!timestamp) return 'N/A';
    const date = new Date(timestamp);
    if (isNaN(date.getTime())) return 'N/A';
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

function calcDurationHM(startDate, endDate) {
    if (!startDate) return 'N/A';
    const start = new Date(startDate);
    const end   = endDate ? new Date(endDate) : new Date();
    let ms      = end - start;
    if (ms < 0) ms = 0;
    
    const hours   = Math.floor(ms / 3600000);
    const minutes = Math.floor((ms % 3600000) / 60000);
    const seconds = Math.floor((ms % 60000) / 1000);
    
    const parts = [];
    if (hours > 0)   parts.push(`${hours}`);
    if (minutes > 0) parts.push(`${minutes}`);
    if (seconds > 0) parts.push(`${seconds}s`);
    
    if (parts.length === 0) return '0s';
    return parts.join(':');
}

// ─── Auth & Role ───────────────────────────────────────────────────────────────

/**
 * Returns the current user's role from localStorage ('ADMIN' or 'USER').
 * Falls back to 'USER' if not set.
 */
function getUserRole() {
    return (localStorage.getItem('user_role') || 'USER').toUpperCase();
}

/**
 * Returns true if the current user has the ADMIN role.
 */
function isCurrentUserAdmin() {
    return getUserRole() === 'ADMIN';
}

// Alias kept for backward compatibility
function isAdmin() { return isCurrentUserAdmin(); }

function getAuthToken()  { return localStorage.getItem('auth_token'); }
function getUsername()   { return localStorage.getItem('username') || 'system'; }

// ─── Role-based UI permissions ─────────────────────────────────────────────────

/**
 * applyRolePermissions()
 *
 * Call once after the DOM is ready. Hides or disables all elements marked
 * with [data-admin-only] when the current user is not ADMIN.
 *
 * Elements can also carry [data-admin-only="disable"] to be disabled (greyed
 * out) rather than fully hidden – useful for buttons inside tables that are
 * generated dynamically and re-checked on each render.
 */
function applyRolePermissions() {
    if (isCurrentUserAdmin()) return; // ADMIN sees everything

    // Hide all admin-only elements
    $('[data-admin-only]').each(function () {
        const mode = $(this).attr('data-admin-only');
        if (mode === 'disable') {
            $(this).prop('disabled', true).addClass('disabled');
        } else {
            $(this).hide();
        }
    });
}

/**
 * Guard function for dynamically-rendered write buttons inside table rows.
 * Returns an empty string (no button) when the user is not ADMIN.
 *
 * Usage in template literals:
 *   ${adminBtn('<button …>Edit</button>')}
 */
function adminBtn(html) {
    return isCurrentUserAdmin() ? html : '';
}

// ─── JSON helpers ──────────────────────────────────────────────────────────────

function prettyJson(str) {
    try {
        if (str && str.trim()) return JSON.stringify(JSON.parse(str), null, 2);
    } catch (e) { /* fall through */ }
    return str || '';
}

function isValidJson(str) {
    if (!str || !str.trim()) return true;
    try { JSON.parse(str); return true; } catch (e) { return false; }
}

// ─── Status badge ──────────────────────────────────────────────────────────────

const STATUS_BADGE = {
    COMPLETED : 'bg-success',
    RUNNING   : 'bg-primary',
    WAITING   : 'bg-info',
    ERROR     : 'bg-danger',
    CANCELED  : 'bg-warning',
    TIMEOUT   : 'bg-warning text-dark'
};

function statusBadge(status) {
    const cls = STATUS_BADGE[status] || 'bg-secondary';
    return `<span class="badge ${cls}">${status}</span>`;
}

// ─── Sort icons ────────────────────────────────────────────────────────────────

function updateSortIcons(column, direction, prefix) {
    prefix = prefix || '';
    $('.sort-icon').attr('class', 'sort-icon none');
    $(`#${prefix}sort-${column}`).attr('class', 'sort-icon ' + direction.toLowerCase());
}

function getCurrentSortFromIcons(prefix) {
    prefix = prefix || '';
    let orderBy = null, sortDirection = null;
    $('.sort-icon').each(function () {
        if ($(this).hasClass('asc')) {
            orderBy = $(this).attr('id').replace(prefix + 'sort-', '');
            sortDirection = 'ASC';
        } else if ($(this).hasClass('desc')) {
            orderBy = $(this).attr('id').replace(prefix + 'sort-', '');
            sortDirection = 'DESC';
        }
    });
    return { orderBy, sortDirection };
}

// ─── Pagination ────────────────────────────────────────────────────────────────

function buildPagination(response, currentPage, paginationId) {
    const $el = $(`#${paginationId}`);
    $el.empty();
    const totalPages = response.pages;
    if (!totalPages || totalPages <= 1) return;

    const prevDisabled = currentPage === 1 ? 'disabled' : '';
    $el.append(`<li class="page-item ${prevDisabled}">
        <a class="page-link" href="#" data-page="${currentPage - 1}" aria-label="Previous">
            <span aria-hidden="true">&laquo;</span>
        </a></li>`);

    const start = Math.max(1, currentPage - 2);
    const end   = Math.min(totalPages, start + 4);
    for (let i = start; i <= end; i++) {
        $el.append(`<li class="page-item ${i === currentPage ? 'active' : ''}">
            <a class="page-link" href="#" data-page="${i}">${i}</a>
        </li>`);
    }

    const nextDisabled = currentPage === totalPages ? 'disabled' : '';
    $el.append(`<li class="page-item ${nextDisabled}">
        <a class="page-link" href="#" data-page="${currentPage + 1}" aria-label="Next">
            <span aria-hidden="true">&raquo;</span>
        </a></li>`);
}

// ─── Toast ─────────────────────────────────────────────────────────────────────

function showToast(title, message, type, options) {
    type    = type    || 'info';
    options = options || {};
    const defaults = { autohide: true, delay: 5000, position: 'bottom-right', closeButton: true };
    const cfg = Object.assign({}, defaults, options);

    const posMap = {
        'top-right'    : 'top-0 end-0',
        'top-left'     : 'top-0 start-0',
        'bottom-right' : 'bottom-0 end-0',
        'bottom-left'  : 'bottom-0 start-0',
        'top-center'   : 'top-0 start-50 translate-middle-x',
        'bottom-center': 'bottom-0 start-50 translate-middle-x'
    };

    let $container = $(`#toast-container-${cfg.position}`);
    if ($container.length === 0) {
        $('body').append(`<div id="toast-container-${cfg.position}" class="position-fixed ${posMap[cfg.position]} p-3" style="z-index:1080"></div>`);
        $container = $(`#toast-container-${cfg.position}`);
    }

    const id       = 'toast-' + Date.now() + '-' + Math.floor(Math.random() * 1000);
    const typeClass = { danger: 'bg-danger', success: 'bg-success', warning: 'bg-warning', info: 'bg-info' }[type] || 'bg-info';
    const textColor = type === 'warning' ? 'text-dark' : 'text-white';
    const closeBtn  = cfg.closeButton
        ? `<button type="button" class="btn-close btn-close-${type === 'warning' ? 'dark' : 'white'}" data-bs-dismiss="toast" aria-label="Close"></button>`
        : '';

    $container.append(`
        <div id="${id}" class="toast" role="alert" aria-live="assertive" aria-atomic="true">
            <div class="toast-header ${typeClass} ${textColor}">
                <strong class="me-auto">${title}</strong>
                <small>just now</small>${closeBtn}
            </div>
            <div class="toast-body">${message}</div>
        </div>`);

    const toastEl = document.getElementById(id);
    const bsToast = new bootstrap.Toast(toastEl, { autohide: cfg.autohide, delay: cfg.delay });
    bsToast.show();
    $(toastEl).on('hidden.bs.toast', function () { $(this).remove(); });
    return { el: toastEl, hide: () => bsToast.hide() };
}

// ─── Confirm Dialog ────────────────────────────────────────────────────────────

function showConfirmDialog(options) {
    options = options || {};
    const cfg = Object.assign({
        title        : 'Confirmation',
        message      : 'Are you sure you want to proceed?',
        okText       : 'Confirm',
        cancelText   : 'Cancel',
        okButtonClass: 'btn-primary',
        onConfirm    : null,
        onCancel     : null
    }, options);

    $('#confirmModalLabel').text(cfg.title);
    $('#confirm-modal .modal-body').html(cfg.message);
    $('#confirm-cancel-btn').text(cfg.cancelText);
    $('#confirm-ok-btn').text(cfg.okText).removeClass().addClass(`btn ${cfg.okButtonClass}`);

    $('#confirm-cancel-btn').off('click').on('click', function () {
        if (typeof cfg.onCancel === 'function') cfg.onCancel();
    });
    $('#confirm-ok-btn').off('click').on('click', function () {
        $('#confirm-modal').modal('hide');
        if (typeof cfg.onConfirm === 'function') cfg.onConfirm();
    });

    const modal = new bootstrap.Modal(document.getElementById('confirm-modal'));
    modal.show();
    return {
        hide         : () => modal.hide(),
        updateMessage: (msg) => $('#confirm-modal .modal-body').html(msg),
        updateTitle  : (t)   => $('#confirmModalLabel').text(t)
    };
}

// ─── Hide description helper (used by task type dropdown) ─────────────────────

function hideTypeDescription() {
    // no-op placeholder kept for compatibility
}
