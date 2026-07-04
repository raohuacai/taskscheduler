/**
 * dashboard.js - Dashboard module (Split Workflow & Task history charts)
 */

function initDashboardModule() {
    $('#refresh-dashboard').on('click', function () {
        loadDashboardData();
    });
}

function clearDashboardRefresh() {
    if (window.dashboardRefreshInterval) {
        clearInterval(window.dashboardRefreshInterval);
        window.dashboardRefreshInterval = null;
    }
}

function loadDashboardData() {
    _loadMonitorStats();
    clearDashboardRefresh();
    window.dashboardRefreshInterval = setInterval(_loadMonitorStats, 60000);
}

function _loadMonitorStats() {
    $('#dashboard .stat-card .number').html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>');

    API.getStats()
        .done(function (data) { _updateDashboard(data); })
        .fail(function (xhr) {
            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load stats data';
            showToast('Error', msg, 'danger');
            $('#dashboard .stat-card .number').text('0');
        });
}

function _updateDashboard(data) {
    if (!data) return;
    if (typeof data === 'string') { try { data = JSON.parse(data); } catch (e) { return; } }

    const g  = data.globalStats          || {};
    const tp = data.threadPool           || {};
    const tw = data.timeWheel            || {};
    const sc = data.scheduler            || {};

    $('#total-workflows').text(g.totalWorkflowsStarted   || 0);
    $('#completed-workflows').text(g.totalWorkflowsCompleted || 0);
    $('#running-workflows').text(g.runningWorkflowsCount  || 0);
    $('#task-count').text(g.totalTasksSubmitted           || 0);
    $('#completed-task').text(g.totalTasksCompleted       || 0);
    $('#failed-count').text(g.totalTasksError             || 0);
	$('#running-tasks').text(g.runningTasksCount          || 0);

    $('#totalScheduledWorkflows').text(sc.totalScheduledWorkflows || 0);
    $('#workflowsExecuted').text(sc.workflowsExecuted            || 0);
    $('#failedExecutions').text(sc.failedExecutions              || 0);
    $('#staleWorkflowsRemoved').text(sc.staleWorkflowsRemoved    || 0);
    $('#scheduleChanges').text(sc.scheduleChanges                || 0);
    $('#maintenanceCycles').text(sc.maintenanceCycles            || 0);

    $('#tasksAdded').text(tw.tasksAdded         || 0);
    $('#tasksExecuted').text(tw.tasksExecuted   || 0);
    $('#totalActiveTasks').text(tw.totalActiveTasks || 0);
    $('#tasksFailed').text(tw.tasksFailed       || 0);
    $('#tasksExpired').text(tw.tasksExpired     || 0);
    $('#oneTimeTasks').text(tw.oneTimeTasks     || 0);

    _updateThreadPoolChart(data.threadPool);
    
    if (data.statsByType) {
        _updateWorkflowHistoryChart(data.statsByType);
        _updateTaskHistoryChart(data.statsByType);
    }
}

function _getLast7Days() {
    const dates = [];
    for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        dates.push(d.toISOString().split('T')[0]);
    }
    return dates;
}

function _extractDailyData(statsArray, targetDates) {
    const completed = [], error = [], cancel = [];
    targetDates.forEach(date => {
        const item = statsArray.find(i => {
            const itemDate = i.startDate ? i.startDate.split('T')[0] : null;
            return itemDate === date;
        });
        completed.push(item ? (item.completedCount || 0) : 0);
        error.push(item ? (item.errorCount || 0) : 0);
        cancel.push(item ? (item.cancelCount || 0) : 0);
    });
    return { completed, error, cancel };
}

function _getEffectiveDates(statsArray) {
    if (!statsArray || statsArray.length === 0) {
        return _getLast7Days();
    }
    const allDates = [...new Set(statsArray.map(item => item.startDate ? item.startDate.split('T')[0] : null).filter(d => d))];
    allDates.sort();
    const lastDates = allDates.slice(-7);
    if (lastDates.length < 7) {
        const generated = _getLast7Days();
        const merged = [...new Set([...generated, ...lastDates])].sort();
        return merged.slice(-7);
    }
    return lastDates;
}

function _updateWorkflowHistoryChart(statsByType) {
    const ctx = document.getElementById('workflow-history-chart');
    if (!ctx) return;
    const badge = document.getElementById('workflow-history-badge');
    
    const workflowData = statsByType?.workflow || [];
    const dates = _getEffectiveDates(workflowData);
    const formattedDates = dates.map(d => new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }));
    const { completed, error, cancel } = _extractDailyData(workflowData, dates);
    
    const totalCompleted = completed.reduce((a,b) => a+b, 0);
    const totalError = error.reduce((a,b) => a+b, 0);
    const totalCancel = cancel.reduce((a,b) => a+b, 0);
    const totalAll = totalCompleted + totalError + totalCancel;
    let successRate = totalAll > 0 ? ((totalCompleted / totalAll) * 100).toFixed(2) : 0;
    
    if (badge) {
        if (totalAll === 0) {
            badge.textContent = `Workflow Success: No data`;
            badge.classList.remove('bg-info', 'bg-warning', 'bg-danger');
            badge.classList.add('bg-secondary');
        } else {
            badge.textContent = `Workflow Success: ${totalCompleted}/${totalAll} (${successRate}%)`;
            badge.classList.remove('bg-secondary', 'bg-info', 'bg-warning', 'bg-danger');
            badge.classList.add(parseFloat(successRate) < 80 ? 'bg-danger' : 'bg-info');
        }
    }
    
    // 计算右侧Y轴最大值：如果最大错误/取消数小于10，则设为10；否则使用最大值*1.2
    let maxErrorCancel = Math.max(...error, ...cancel, 0);
    if (maxErrorCancel < 10) {
        maxErrorCancel = 10;
    }
    const y1Max = maxErrorCancel * 1.2;
    
    const chartData = {
        labels: formattedDates,
        datasets: [
            { type: 'bar', label: 'Completed', data: completed, backgroundColor: 'rgba(40,167,69,0.8)', yAxisID: 'y', borderRadius: 4, barPercentage: 0.7 },
            { type: 'bar', label: 'Error', data: error, backgroundColor: 'rgba(220,53,69,0.8)', yAxisID: 'y1', borderRadius: 4, barPercentage: 0.7 },
            { type: 'bar', label: 'Canceled', data: cancel, backgroundColor: 'rgba(253,126,20,0.8)', yAxisID: 'y1', borderRadius: 4, barPercentage: 0.7 }
        ]
    };
    
    const config = {
        type: 'bar',
        data: chartData,
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: { position: 'top', display: true },
                tooltip: { mode: 'index', intersect: false },
                //title: { display: true, text: totalAll === 0 ? 'Workflow Daily Statistics (No data)' : `Workflow Daily Statistics (Success: ${successRate}%)`, font: { size: 12 } }
            },
            scales: {
                y: { beginAtZero: true, title: { display: true, text: 'Completed Count' }, grid: { drawOnChartArea: true } },
                y1: { beginAtZero: true, max: y1Max, title: { display: true, text: 'Error / Cancel Count' }, grid: { drawOnChartArea: false }, position: 'right' }
            }
        }
    };
    
    if (window.workflowHistoryChart) {
        window.workflowHistoryChart.data = chartData;
        window.workflowHistoryChart.options = config.options;
        window.workflowHistoryChart.update();
    } else {
        window.workflowHistoryChart = new Chart(ctx, config);
    }
    if (window.workflowHistoryChart) window.workflowHistoryChart.resize();
}

function _updateTaskHistoryChart(statsByType) {
    const ctx = document.getElementById('task-history-chart');
    if (!ctx) return;
    const badge = document.getElementById('task-history-badge');
    
    const taskData = statsByType?.task || [];
    const dates = _getEffectiveDates(taskData);
    const formattedDates = dates.map(d => new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }));
    const { completed, error, cancel } = _extractDailyData(taskData, dates);
    
    const totalCompleted = completed.reduce((a,b) => a+b, 0);
    const totalError = error.reduce((a,b) => a+b, 0);
    const totalCancel = cancel.reduce((a,b) => a+b, 0);
    const totalAll = totalCompleted + totalError + totalCancel;
    let successRate = totalAll > 0 ? ((totalCompleted / totalAll) * 100).toFixed(2) : 0;
    
    if (badge) {
        if (totalAll === 0) {
            badge.textContent = `Task Success: No data`;
            badge.classList.remove('bg-info', 'bg-warning', 'bg-danger');
            badge.classList.add('bg-secondary');
        } else {
            badge.textContent = `Task Success: ${totalCompleted}/${totalAll} (${successRate}%)`;
            badge.classList.remove('bg-secondary', 'bg-info', 'bg-warning', 'bg-danger');
            badge.classList.add(parseFloat(successRate) < 80 ? 'bg-danger' : 'bg-info');
        }
    }
    
    // 计算右侧Y轴最大值：如果最大错误/取消数小于10，则设为10；否则使用最大值*1.2
    let maxErrorCancel = Math.max(...error, ...cancel, 0);
    if (maxErrorCancel < 10) {
        maxErrorCancel = 10;
    }
    const y1Max = maxErrorCancel * 1.2;
    
    const chartData = {
        labels: formattedDates,
        datasets: [
            { type: 'bar', label: 'Completed', data: completed, backgroundColor: 'rgba(40,167,69,0.8)', yAxisID: 'y', borderRadius: 4, barPercentage: 0.7 },
            { type: 'bar', label: 'Error', data: error, backgroundColor: 'rgba(220,53,69,0.8)', yAxisID: 'y1', borderRadius: 4, barPercentage: 0.7 },
            { type: 'bar', label: 'Canceled', data: cancel, backgroundColor: 'rgba(253,126,20,0.8)', yAxisID: 'y1', borderRadius: 4, barPercentage: 0.7 }
        ]
    };
    
    const config = {
        type: 'bar',
        data: chartData,
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: { position: 'top', display: true },
                tooltip: { mode: 'index', intersect: false },
                //title: { display: true, text: totalAll === 0 ? 'Task Daily Statistics (No data)' : `Task Daily Statistics (Success: ${successRate}%)`, font: { size: 12 } }
            },
            scales: {
                y: { beginAtZero: true, title: { display: true, text: 'Completed Count' }, grid: { drawOnChartArea: true } },
                y1: { beginAtZero: true, max: y1Max, title: { display: true, text: 'Error / Cancel Count' }, grid: { drawOnChartArea: false }, position: 'right' }
            }
        }
    };
    
    if (window.taskHistoryChart) {
        window.taskHistoryChart.data = chartData;
        window.taskHistoryChart.options = config.options;
        window.taskHistoryChart.update();
    } else {
        window.taskHistoryChart = new Chart(ctx, config);
    }
    if (window.taskHistoryChart) window.taskHistoryChart.resize();
}

function _updateThreadPoolChart(tp) {
    if (!tp) return;
    const ctx = document.getElementById('thread-pool-chart');
    if (!ctx) return;
    const badge = document.getElementById('thread-pool-badge');

    const maxPool = tp.maximumPoolSize || 0;
    const corePool = tp.corePoolSize || 0;
    const curPool = tp.currentPoolSize || 0;
    const queueSize = tp.queueSize || 0;
    const active = tp.activeThreads || 0;
    const idle = tp.idleThreads || 0;

    if (badge) {
        badge.textContent = `${active}/${maxPool}`;
        badge.classList.remove('bg-primary', 'bg-warning', 'bg-danger');
        const pct = maxPool > 0 ? (active / maxPool) * 100 : 0;
        badge.classList.add(pct < 50 ? 'bg-primary' : pct < 80 ? 'bg-warning' : 'bg-danger');
    }

    const chartData = {
        labels: ['Maximum Pool Size', 'Core Pool Size', 'Current Pool Size', 'Active Threads', 'Idle Threads', 'Queued Tasks'],
        datasets: [{
            label: 'Thread Pool Metrics',
            data: [maxPool, corePool, curPool, active, idle, queueSize],
            backgroundColor: ['rgba(40,167,69,0.8)', 'rgba(108,117,125,0.6)', 'rgba(255,193,7,0.8)', 'rgba(255,13,77,0.8)', 'rgba(25,193,17,0.8)', 'rgba(255,56,7,0.8)']
        }]
    };
    const titleText = `Thread Pool: ${active} Active / ${maxPool} Max, ${queueSize} Queued`;
    const chartOpts = {
        type: 'bar',
        data: chartData,
        options: {
            responsive: true, maintainAspectRatio: true,
            plugins: { legend: { position: 'top', display: false }, title: { display: true, text: titleText } },
            scales: { y: { beginAtZero: true, suggestedMax: Math.max(maxPool, corePool, curPool, queueSize, active, idle) * 1.2 } }
        }
    };

    if (window.threadPoolChart) {
        window.threadPoolChart.data = chartData;
        window.threadPoolChart.options.plugins.title.text = titleText;
        window.threadPoolChart.update();
    } else {
        window.threadPoolChart = new Chart(ctx.getContext('2d'), chartOpts);
    }
    if (window.threadPoolChart) window.threadPoolChart.resize();
}