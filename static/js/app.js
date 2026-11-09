/**
 * app.js - Application entry point, navigation & authentication
 * Task Scheduler Web UI
 */

let currentSection = 'dashboard';

// Sections only ADMIN may see in the nav or enter.
// global-params is admin-only because the API returns plaintext secrets
// (e.g. the EmailConfig SMTP password).
const ADMIN_ONLY_SECTIONS = ['manage-users', 'global-params'];

$(document).ready(function () {

    // ── Auth check ─────────────────────────────────────────────
    if (!localStorage.getItem('auth_token')) {
        window.location.href = 'login.html';
        return;
    }

    // ── Global AJAX auth header ────────────────────────────────
    $.ajaxSetup({
        beforeSend: function (xhr) {
            const token = localStorage.getItem('auth_token');
            if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token);
        }
    });

    // ── Global 401 handler ─────────────────────────────────────
    $(document).ajaxError(function (event, jqxhr) {
        if (jqxhr.status === 401) {
            localStorage.removeItem('auth_token');
            localStorage.removeItem('username');
            showToast('Session Expired', 'Your session has expired. Redirecting to login page...', 'warning');
            setTimeout(function () { window.location.href = 'login.html'; }, 2000);
        }
    });

    // ── Display username ───────────────────────────────────────
    const username = localStorage.getItem('username');
    const userRole = localStorage.getItem('user_role') || 'USER';
    if (username) $('#username-display').text(`${username} (${userRole})`);

    // ── Navigation setup ───────────────────────────────────────
    _setupNavigation();

    // ── Logout ─────────────────────────────────────────────────
    $('#logout-btn').on('click', function (e) {
        e.preventDefault();
        localStorage.removeItem('auth_token');
        localStorage.removeItem('username');
        localStorage.removeItem('user_role');
        window.location.href = 'login.html';
    });

    // ── About ──────────────────────────────────────────────────
    $('#about-btn').on('click', function (e) {
        e.preventDefault();
        $('#about-modal').modal('show');
    });

    // ── Initialize all modules ─────────────────────────────────
    initDashboardModule();
    initGlobalParamsModule();
    initTaskTypeModule();
    initWorkflowModule();
    initTaskModule();
    initDAGModule();
    initSchedulerModule();
    initMonitoringModule();
    initHistoryModule();
    initLockModule();
    initUsersModule();

    // ── Apply role-based UI permissions ───────────────────────
    applyRolePermissions();

    // ── Load initial dashboard data ────────────────────────────
    loadDashboardData();
});

// ─── Navigation ────────────────────────────────────────────────────────────────

function _setupNavigation() {
    const isAdmin = isCurrentUserAdmin();
    if (!isAdmin) {
        // USER role: hide every admin-only nav section
        // (task-types and the remaining sections stay visible, read-only for USER)
        ADMIN_ONLY_SECTIONS.forEach(function (section) {
            $(`.nav-link[data-target="${section}"]`).parent().hide();
        });
    }

    $('.nav-link').on('click', function (e) {
        e.preventDefault();
        navigateTo($(this).data('target'));
    });

    if (!isAdmin && ADMIN_ONLY_SECTIONS.includes(currentSection)) {
        navigateTo('dashboard');
    }
}

function navigateTo(section) {
    const isAdmin = isCurrentUserAdmin();
    if (!isAdmin && ADMIN_ONLY_SECTIONS.includes(section)) {
        showToast('Access Denied', 'You do not have permission to access this section.', 'warning');
        return;
    }

    // Clean up dashboard timer when leaving
    if (currentSection === 'dashboard' && section !== 'dashboard') {
        clearDashboardRefresh();
    }

    currentSection = section;
    $('.nav-link').removeClass('active');
    $(`.nav-link[data-target="${section}"]`).addClass('active');
    $('.content-area').removeClass('active');
    $(`#${section}`).addClass('active');

    // Section-specific on-enter actions
    switch (section) {
        case 'dashboard':
            loadDashboardData();
            break;
        case 'global-params':
            loadGlobalParams();
            break;
        case 'task-types':
            loadTaskTypeData(1, 10, 'TYPE_GROUP', 'ASC');
            break;
        case 'workflow-def':
            loadWorkflowData(1, 10, 'WORKFLOW_NAME', 'ASC');
            break;
        case 'workflow-dag':
            WorkflowDAG.loadTaskColors();
            WorkflowDAG.loadWorkflowList();
            break;
        case 'scheduler':
            loadSchedulerData(1, 10, 'WORKFLOW_NAME', 'ASC');
            break;
        case 'monitoring': {
            const today = getLocalToday();
            $('#monitor-start-date').val(today);
            loadMonitoringData(1, 10, 'START_DATE', 'DESC');
            break;
        }
        case 'history': {
            const today = getLocalToday();
            $('#history-start-date').val(today);
            loadHistoryData(1, 15);
            break;
        }
        case 'locks':
            loadLockData();
            break;
        case 'manage-users':
            loadUsersData();
            break;
    }
}

// ─── DAG module bridge ──────────────────────────────────────────────────────────

function initDAGModule() {
    if (typeof WorkflowDAG !== 'undefined') {
        WorkflowDAG.initialize();
        // Pre-load task colors and workflow list on page ready so the
        // dropdown is populated immediately when the user first enters
        // the workflow-dag section, without needing to click Refresh.
        WorkflowDAG.loadTaskColors();
        WorkflowDAG.loadWorkflowList();
    } else {
        console.error('WorkflowDAG module not loaded');
    }
}
