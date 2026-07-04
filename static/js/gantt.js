/**
 * gantt.js - Gantt chart module with workflow collapse/expand
 * Triggered from the Monitoring page; fetches the full workflow tree
 * for a given topParentInstanceId via the history API and renders a
 * read-only Gantt chart inside #gantt-modal.
 */

// ═══════════════════════════════════════════════════════════════
//  CONSTANTS
// ═══════════════════════════════════════════════════════════════

const GANTT_LABEL_W     = 220;
const GANTT_ROW_H       = 26;
const GANTT_TASK_INDENT = 18;
const GANTT_BAR_PADY    = 5;
const GANTT_HEADER_H    = 34;
const GANTT_MIN_BAR_W   = 4;
const GANTT_TICK_COUNT  = 6;

const GANTT_STATUS_CLASS = {
    COMPLETED : 'node-completed',
    RUNNING   : 'node-running',
    ERROR     : 'node-error',
    CANCELED  : 'node-canceled',
    WAITING   : 'node-waiting',
    TIMEOUT   : 'node-timeout',
    SKIPPED   : 'node-skipped'
};

const GANTT_LEGEND_ITEMS = [
    { status: 'COMPLETED', label: 'Completed' },
    { status: 'RUNNING',   label: 'Running'   },
    { status: 'ERROR',     label: 'Error'     },
    { status: 'CANCELED',  label: 'Canceled'  },
    { status: 'WAITING',   label: 'Waiting'   },
    { status: 'TIMEOUT',   label: 'Timeout'   },
    { status: 'SKIPPED',   label: 'Skipped'   }
];

// Runtime colour cache
const _ganttColorCache = {};

// Collapse state: true = collapsed (tasks hidden), false/undefined = expanded
// Default: all workflows start collapsed (we set them to true on first load)
let _ganttCollapsed = {};

// ═══════════════════════════════════════════════════════════════
//  COLOUR HELPER
// ═══════════════════════════════════════════════════════════════

function _getStatusColor(status) {
    const cls = GANTT_STATUS_CLASS[status] || 'node-skipped';
    if (_ganttColorCache[cls]) return _ganttColorCache[cls];

    const probe = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    probe.setAttribute('class', cls);
    probe.style.cssText = 'position:absolute;visibility:hidden;animation:none;transform:none;';
    document.body.appendChild(probe);
    const color = window.getComputedStyle(probe).fill || '#B4B2A9';
    document.body.removeChild(probe);

    _ganttColorCache[cls] = color;
    return color;
}

// ═══════════════════════════════════════════════════════════════
//  PUBLIC ENTRY POINT
// ═══════════════════════════════════════════════════════════════

function openGanttModal(topParentInstanceId) {
    // Reset collapse state: all workflows will be collapsed
    _ganttCollapsed = {};

    $('#gantt-modal-title').text('Gantt – Instance ' + topParentInstanceId);
    $('#gantt-modal-body').html(
        '<div class="text-center py-5">' +
        '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>' +
        'Loading Gantt data...</div>'
    );
    new bootstrap.Modal('#gantt-modal').show();

    API.getHistory({
        topParentInstanceId : topParentInstanceId,
        pageSize            : 500,
        pageNum             : 1
    })
    .done(function (r) {
        const list = r.list || [];
        if (list.length === 0) {
            $('#gantt-modal-body').html('<div class="text-center text-muted py-5">No records found.</div>');
            return;
        }
        const ganttData = _parseGanttData(list);
        // Initially collapse all workflows (set to true)
        ganttData.workflowRows.forEach(function(wf) {
            if (_ganttCollapsed[wf.id] === undefined) {
                _ganttCollapsed[wf.id] = true; // true = collapsed
            }
        });
        _renderGanttChart(ganttData);
    })
    .fail(function (xhr) {
        const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load Gantt data';
        $('#gantt-modal-body').html('<div class="text-center text-danger py-5">' + msg + '</div>');
    });
}

// ═══════════════════════════════════════════════════════════════
//  DATA PARSING
// ═══════════════════════════════════════════════════════════════

function _parseGanttData(list) {
    let minTime = Infinity;
    let maxTime = -Infinity;

    // Sort workflows
    const sorted = list.slice().sort(function (a, b) {
        const as = a.startDate ? new Date(a.startDate).getTime() : Infinity;
        const bs = b.startDate ? new Date(b.startDate).getTime() : Infinity;
        if (as !== bs) return as - bs;
        const ae = a.endDate ? new Date(a.endDate).getTime() : Infinity;
        const be = b.endDate ? new Date(b.endDate).getTime() : Infinity;
        if (ae !== be) return ae - be;
        return String(a.instanceId).localeCompare(String(b.instanceId));
    });

    const workflowRows = [];
    const tasksMap = {};

    sorted.forEach(function (inst) {
        const wfStart = inst.startDate ? new Date(inst.startDate).getTime() : null;
        const wfEnd   = inst.endDate   ? new Date(inst.endDate).getTime()   : Date.now();

        if (wfStart !== null) minTime = Math.min(minTime, wfStart);
        maxTime = Math.max(maxTime, wfEnd);

        // Sort tasks within this workflow
        const sortedTasks = (inst.tasks || []).slice().sort(function (a, b) {
            const as = a.startDate ? new Date(a.startDate).getTime() : Infinity;
            const bs = b.startDate ? new Date(b.startDate).getTime() : Infinity;
            if (as !== bs) return as - bs;
            const ae = a.endDate ? new Date(a.endDate).getTime() : Infinity;
            const be = b.endDate ? new Date(b.endDate).getTime() : Infinity;
            if (ae !== be) return ae - be;
            return String(a.taskInstanceId).localeCompare(String(b.taskInstanceId));
        });

        const wfRow = {
            id       : inst.instanceId,
            label    : inst.workflowName || inst.workflowId || inst.instanceId,
            subLabel : String(inst.instanceId),
            type     : 'workflow',
            status   : inst.status,
            startRaw : inst.startDate || null,
            endRaw   : inst.endDate   || null,
            start    : wfStart,
            end      : wfEnd,
            taskCount: sortedTasks.length
        };
        workflowRows.push(wfRow);

        const tasks = [];
        sortedTasks.forEach(function (task) {
            const tStart = task.startDate ? new Date(task.startDate).getTime() : wfStart;
            const tEnd   = task.endDate   ? new Date(task.endDate).getTime()   : Date.now();

            if (tStart !== null) minTime = Math.min(minTime, tStart);
            maxTime = Math.max(maxTime, tEnd);

            const taskRow = {
                id       : task.taskInstanceId,
                label    : task.taskName || String(task.taskInstanceId),
                subLabel : task.taskType || '',
                type     : 'task',
                status   : task.status,
                startRaw : task.startDate || null,
                endRaw   : task.endDate   || null,
                start    : tStart,
                end      : tEnd,
                parentId : inst.instanceId
            };
            tasks.push(taskRow);
        });
        tasksMap[inst.instanceId] = tasks;
    });

    if (!isFinite(minTime)) minTime = Date.now() - 3600000;
    if (!isFinite(maxTime)) maxTime = Date.now();

    const margin = Math.max((maxTime - minTime) * 0.02, 60000);
    minTime -= margin;
    maxTime += margin;

    return { workflowRows, tasksMap, minTime, maxTime };
}

// ═══════════════════════════════════════════════════════════════
//  RENDERING
// ═══════════════════════════════════════════════════════════════

function _renderGanttChart(ganttData) {
    const { workflowRows, tasksMap, minTime, maxTime } = ganttData;
    const timeSpan = maxTime - minTime || 1;

    // Build visible rows based on collapse state
    // _ganttCollapsed[id] === true means collapsed (tasks hidden)
    const visibleRows = [];
    workflowRows.forEach(function(wf) {
        visibleRows.push(wf);
        const isExpanded = (_ganttCollapsed[wf.id] !== true); // true = expanded
        if (isExpanded) {
            const tasks = tasksMap[wf.id] || [];
            visibleRows.push.apply(visibleRows, tasks);
        }
    });

    const containerW = Math.max($('#gantt-svg-wrap').width() || 900, 500);
    const chartW     = containerW - GANTT_LABEL_W - 2;
    const totalH     = GANTT_HEADER_H + visibleRows.length * GANTT_ROW_H + 4;

    function toPx(ms) {
        return GANTT_LABEL_W + ((ms - minTime) / timeSpan) * chartW;
    }

    let svg = '<svg xmlns="http://www.w3.org/2000/svg"'
            + ' width="' + containerW + '" height="' + totalH + '"'
            + ' style="display:block;font-family:inherit;">';

    // Time-axis header
    svg += '<rect x="0" y="0" width="' + containerW + '" height="' + GANTT_HEADER_H + '"'
         + ' fill="#f8f9fa" stroke="#dee2e6" stroke-width="0.5"/>';

    const ticks = _buildTimeTicks(minTime, maxTime, GANTT_TICK_COUNT);
    ticks.forEach(function (t) {
        const x = toPx(t.ms);
        svg += '<line x1="' + x + '" y1="' + GANTT_HEADER_H + '"'
             + ' x2="' + x + '" y2="' + totalH + '"'
             + ' stroke="#dee2e6" stroke-width="0.5" stroke-dasharray="3 3"/>';
        svg += '<text x="' + x + '" y="' + (GANTT_HEADER_H / 2 + 5) + '"'
             + ' text-anchor="middle" font-size="11" fill="#6c757d">'
             + _escapeXml(t.label) + '</text>';
    });

    svg += '<line x1="' + GANTT_LABEL_W + '" y1="0"'
         + ' x2="' + GANTT_LABEL_W + '" y2="' + totalH + '"'
         + ' stroke="#dee2e6" stroke-width="1"/>';

    // Rows
    visibleRows.forEach(function (row, idx) {
        const rowY   = GANTT_HEADER_H + idx * GANTT_ROW_H;
        const isTask = (row.type === 'task');
        let indent   = isTask ? GANTT_TASK_INDENT : 4;

        // Alternating row stripe
        const rowBg = (idx % 2 === 0) ? 'transparent' : 'rgba(0,0,0,0.02)';
        svg += '<rect x="0" y="' + rowY + '" width="' + containerW + '"'
             + ' height="' + GANTT_ROW_H + '" fill="' + rowBg + '"/>';

        svg += '<line x1="0" y1="' + (rowY + GANTT_ROW_H) + '"'
             + ' x2="' + containerW + '" y2="' + (rowY + GANTT_ROW_H) + '"'
             + ' stroke="#dee2e6" stroke-width="0.5"/>';

        // Workflow toggle icon (only for workflows with tasks)
        if (!isTask && row.taskCount > 0) {
            const isExpanded = (_ganttCollapsed[row.id] !== true);
            // collapsed = !isExpanded (show '+' when collapsed)
            const iconX = 4;
            const iconY = rowY + GANTT_ROW_H / 2;
            svg += '<g class="gantt-toggle" data-wfid="' + _escapeXml(String(row.id)) + '"'
                 + ' style="cursor:pointer;">'
                 + '<rect x="' + iconX + '" y="' + (iconY - 7) + '"'
                 + ' width="14" height="14" rx="2"'
                 + ' fill="#fff" stroke="#6c757d" stroke-width="1"/>'
                 + '<line x1="' + (iconX + 3) + '" y1="' + iconY + '"'
                 + ' x2="' + (iconX + 11) + '" y2="' + iconY + '"'
                 + ' stroke="#6c757d" stroke-width="1.5"/>';
            if (!isExpanded) {
                // collapsed: add vertical line for '+'
                svg += '<line x1="' + (iconX + 7) + '" y1="' + (iconY - 4) + '"'
                     + ' x2="' + (iconX + 7) + '" y2="' + (iconY + 4) + '"'
                     + ' stroke="#6c757d" stroke-width="1.5"/>';
            }
            svg += '</g>';
            indent += 18; // shift text right
        }

        // Label background for alert statuses
        const statusColor = _getStatusColor(row.status);
        const isAlert = ['ERROR', 'TIMEOUT', 'CANCELED'].indexOf((row.status || '').toUpperCase()) !== -1;
        const maxChars = Math.floor((GANTT_LABEL_W - indent - 8) / 6.5);
        const labelText = row.label.length > maxChars ? row.label.slice(0, maxChars - 1) + '…' : row.label;
        const fontSize = isTask ? 11 : 12;
        const fontWeight = isTask ? 'normal' : '500';
        const labelY = rowY + GANTT_ROW_H / 2;

        if (isAlert) {
            svg += '<rect x="' + indent + '" y="' + (rowY + 2) + '"'
                 + ' width="' + (GANTT_LABEL_W - indent - 2) + '" height="' + (GANTT_ROW_H - 4) + '"'
                 + ' rx="3" fill="' + statusColor + '"/>';
        }

        svg += '<text x="' + (indent + 4) + '" y="' + labelY + '"'
             + ' dominant-baseline="central"'
             + ' font-size="' + fontSize + '" font-weight="' + fontWeight + '"'
             + ' fill="#212529">' + _escapeXml(labelText) + '</text>';

        // Gantt bar (skip WAITING)
        if (row.start !== null && (row.status || '').toUpperCase() !== 'WAITING') {
            const barX      = toPx(row.start);
            const rawBarW   = toPx(row.end) - barX;
            const barW      = Math.max(rawBarW, GANTT_MIN_BAR_W);
            const barY      = rowY + GANTT_BAR_PADY;
            const barH      = GANTT_ROW_H - GANTT_BAR_PADY * 2;
            const color     = statusColor;
            const rx        = isTask ? 2 : 3;
            const opacity   = isTask ? '0.75' : '0.90';
            const rightEdge = GANTT_LABEL_W + chartW;

            const clippedRight = (barX + barW) > rightEdge;
            const drawW        = clippedRight ? (rightEdge - barX) : barW;

            svg += '<rect x="' + barX + '" y="' + barY + '"'
                 + ' width="' + drawW + '" height="' + barH + '"'
                 + ' rx="' + rx + '" fill="' + color + '" opacity="' + opacity + '">'
                 + '<title>' + _escapeXml(_buildTooltipText(row)) + '</title>'
                 + '</rect>';

            if (clippedRight) {
                const tmid = barY + barH / 2;
                svg += '<polygon points="'
                     + rightEdge + ',' + barY + ' '
                     + (rightEdge + 6) + ',' + tmid + ' '
                     + rightEdge + ',' + (barY + barH) + '"'
                     + ' fill="' + color + '" opacity="0.95"/>';
            }

            if (drawW > 48) {
                const dur = calcDurationHM(row.startRaw, row.endRaw);
                svg += '<text x="' + (barX + drawW / 2) + '" y="' + (barY + barH / 2) + '"'
                     + ' dominant-baseline="central" text-anchor="middle"'
                     + ' font-size="10" fill="#fff" pointer-events="none">'
                     + _escapeXml(dur) + '</text>';
            }
        }
    });

    svg += '</svg>';

    // Legend
    let legend = '<div class="d-flex flex-wrap gap-3 mt-2 px-1" style="font-size:12px;color:#6c757d;">';
    GANTT_LEGEND_ITEMS.forEach(function (item) {
        const cls = GANTT_STATUS_CLASS[item.status] || 'node-skipped';
        legend += '<span class="d-flex align-items-center gap-1">'
                + '<svg width="12" height="12" style="flex-shrink:0;overflow:visible;">'
                + '<circle cx="6" cy="6" r="6" class="' + cls + '"'
                + ' style="animation:none;transform:none;transform-origin:center;"/>'
                + '</svg>'
                + _escapeXml(item.label) + '</span>';
    });
    legend += '</div>';

    $('#gantt-svg-wrap').html(svg);
    $('#gantt-legend-wrap').html(legend);

    // Bind toggle click events
    $('#gantt-svg-wrap').off('click', '.gantt-toggle').on('click', '.gantt-toggle', function(e) {
        const wfId = $(this).data('wfid');
        if (wfId) {
            // Toggle: if currently collapsed (true) → expanded (false/undefined), else collapsed
            _ganttCollapsed[wfId] = !(_ganttCollapsed[wfId] === true);
            _renderGanttChart(ganttData);
        }
    });
	
	$('#export-gantt-svg-btn').off('click').on('click', exportGanttAsSVG);
}

// ═══════════════════════════════════════════════════════════════
//  HELPERS
// ═══════════════════════════════════════════════════════════════

function _buildTimeTicks(minTime, maxTime, count) {
    const ticks = [];
    const interval = (maxTime - minTime) / (count - 1);
    for (let i = 0; i < count; i++) {
        const ms = minTime + i * interval;
        ticks.push({ ms: ms, label: _formatTickLabel(ms, maxTime - minTime) });
    }
    return ticks;
}

function _formatTickLabel(ms, span) {
    const d   = new Date(ms);
    const pad = function (n) { return String(n).padStart(2, '0'); };
    const hhmm = pad(d.getHours()) + ':' + pad(d.getMinutes());
    const mmdd = pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    if (span < 86400000)  return hhmm;
    if (span < 604800000) return mmdd + ' ' + hhmm;
    return mmdd;
}

function _buildTooltipText(row) {
    const start = row.startRaw ? new Date(row.startRaw).toLocaleString() : '-';
    const end   = row.endRaw   ? new Date(row.endRaw).toLocaleString()   : '-';
    const dur   = calcDurationHM(row.startRaw, row.endRaw);
    const lines = [
        row.label,
        'ID: '       + row.id,
        'Status: '   + (row.status || '-'),
        'Start:  '   + start,
        'End:    '   + end,
        'Duration: ' + dur
    ];
    if (row.subLabel) lines.splice(1, 0, row.subLabel);
    return lines.join('\n');
}

function _escapeXml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

// ═══════════════════════════════════════════════════════════════
//  EXPORT SVG FUNCTION
// ═══════════════════════════════════════════════════════════════

function exportGanttAsSVG() {
    const svgElement = $('#gantt-svg-wrap svg')[0];
    if (!svgElement) {
        if (typeof showToast === 'function') {
            showToast('Warning', 'No SVG to export', 'warning');
        } else {
            console.warn('No SVG to export');
        }
        return;
    }

    const clonedSvg = svgElement.cloneNode(true);
    clonedSvg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    const serializer = new XMLSerializer();
    let source = serializer.serializeToString(clonedSvg);
    source = '<?xml version="1.0" standalone="no"?>\r\n' + source;

    const blob = new Blob([source], { type: 'image/svg+xml' });
    const url = URL.createObjectURL(blob);
    const downloadLink = document.createElement('a');
    downloadLink.href = url;
    downloadLink.download = `gantt_${new Date().toISOString().slice(0,19).replace(/:/g, '-')}.svg`;
    document.body.appendChild(downloadLink);
    downloadLink.click();
    document.body.removeChild(downloadLink);
    URL.revokeObjectURL(url);
}

// 在 _renderGanttChart 末尾绑定（已存在，无需额外 ready 绑定）
// $('#export-gantt-svg-btn').off('click').on('click', exportGanttAsSVG);