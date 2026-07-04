/**
 * dag.js - Unified DAG module
 *
 * WorkflowDAG : jsPlumb-based editor with rectangular nodes (editable)
 * MonitorDAG  : D3-based viewer with circular nodes (read-only + auto-refresh)
 *
 * Shared: DAGLayout (hierarchical layer algorithm + crossing minimization)
 */

// ═══════════════════════════════════════════════════════════════
//  SHARED: DAG LAYOUT ENGINE
// ═══════════════════════════════════════════════════════════════

const DAGLayout = (function () {

    function computeLayers(nodes, edges) {
        const nodeMap = {};
        nodes.forEach(function (n) {
            nodeMap[n.id] = { id: n.id, layer: -1, predecessors: [], successors: [] };
        });
        edges.forEach(function (e) {
            if (nodeMap[e.source] && nodeMap[e.target]) {
                nodeMap[e.source].successors.push(e.target);
                nodeMap[e.target].predecessors.push(e.source);
            }
        });

        const roots = Object.values(nodeMap).filter(function (n) { return n.predecessors.length === 0; });
        roots.forEach(function (n) { n.layer = 0; });

        let current = roots.slice();
        let layer = 1;
        while (current.length > 0) {
            const next = [];
            current.forEach(function (node) {
                node.successors.forEach(function (sid) {
                    const s = nodeMap[sid];
                    if (!s) return;
                    const allDone = s.predecessors.every(function (pid) { return nodeMap[pid] && nodeMap[pid].layer !== -1; });
                    if (allDone && s.layer === -1) {
                        s.layer = Math.max(...s.predecessors.map(function (pid) { return nodeMap[pid].layer; })) + 1;
                        next.push(s);
                    }
                });
            });
            if (next.length === 0) {
                const unset = Object.values(nodeMap).filter(function (n) { return n.layer === -1; });
                unset.forEach(function (n) { n.layer = layer; next.push(n); });
            }
            current = next;
            layer++;
        }

        const maxLayer = Math.max(...Object.values(nodeMap).map(function (n) { return n.layer; }));
        const layers = [];
        for (let i = 0; i <= maxLayer; i++) {
            layers[i] = Object.values(nodeMap).filter(function (n) { return n.layer === i; }).map(function (n) { return n.id; });
        }
        return { nodeMap, layers };
    }

    function minimizeCrossings(layers, nodeMap) {
        for (let iter = 0; iter < 5; iter++) {
            let improved = false;
            for (let li = 1; li < layers.length; li++) {
                const layer = layers[li];
                const prev  = layers[li - 1];
                const bary  = {};
                layer.forEach(function (id) {
                    const preds = (nodeMap[id] ? nodeMap[id].predecessors : []).filter(function (p) { return prev.indexOf(p) !== -1; });
                    bary[id] = preds.length > 0
                        ? preds.reduce(function (s, p) { return s + prev.indexOf(p); }, 0) / preds.length
                        : prev.length;
                });
                const sorted = layer.slice().sort(function (a, b) { return bary[a] - bary[b]; });
                const changed = sorted.some(function (id, i) { return id !== layer[i]; });
                if (changed) { layers[li] = sorted; improved = true; }
            }
            if (!improved) break;
        }
        return layers;
    }

    return { computeLayers, minimizeCrossings };
})();


// ═══════════════════════════════════════════════════════════════
//  WORKFLOW DAG MODULE  (jsPlumb, rectangular nodes, editable)
// ═══════════════════════════════════════════════════════════════

const WorkflowDAG = (function () {

    // ── Private state ──────────────────────────────────────────
    let _jspInstance     = null;
    let _currentWfId     = null;
    let _currentWfName   = null;
    let _taskNodes       = {};
    let _taskMap         = {};
    let _workflowList    = [];
    let _taskTypeMap     = {};
    let _zoomMap         = {};
    let _currentZoom     = 1.0;

    // ── jsPlumb init ───────────────────────────────────────────
    function _initJsPlumb() {
        if (_jspInstance) return;
        _jspInstance = jsPlumb.getInstance({
            Endpoint          : ['Dot', { radius: 4 }],
            Connector         : ['Bezier', { curviness: 50 }],
            HoverPaintStyle   : { stroke: '#1e8151', strokeWidth: 2 },
            ConnectionOverlays: [['Arrow', { location: 1, id: 'arrow', width: 10, length: 10, foldback: 0.8 }]],
            Container         : 'dag-canvas',
            DragOptions       : { cursor: 'pointer', zIndex: 2000 }
        });

        _jspInstance.bind('ready', function () {
            _jspInstance.registerConnectionType('basic', {
                anchor     : ['Continuous', { faces: ['left', 'right'] }],
                connector  : ['Bezier', { curviness: 63 }],
                paintStyle : { stroke: '#456', strokeWidth: 2 },
                hoverPaintStyle: { stroke: '#1e8151', strokeWidth: 3 }
            });
            _jspInstance.bind('connection', function (info, origEvt) {
                if (origEvt) {
                    if (_hasCycle()) {
                        showToast('Validation Error', 'Cannot create connection: Would create a cycle', 'danger');
                        _jspInstance.deleteConnection(info.connection);
                    } else {
                        _updatePredecessors();
                    }
                }
            });
            _jspInstance.bind('connectionDetached', function (info, origEvt) {
                if (origEvt) _updatePredecessors();
            });
        });
    }

    // ── Task colors ────────────────────────────────────────────
    function loadTaskColors() {
        _taskTypeMap = {};
        API.getTaskTypes({ pageSize: 50 })
            .done(function (r) {
                if (r && r.records) {
                    r.records.forEach(function (tt) { _taskTypeMap[tt.typeName] = tt; });
                }
            })
            .fail(function () { console.error('Failed to load task types'); });
    }

    function _getColor(taskType) {
        if (taskType && _taskTypeMap[taskType] && _taskTypeMap[taskType].taskColor) {
            return _taskTypeMap[taskType].taskColor;
        }
        return '#6c757d';
    }

    // ── Workflow list ──────────────────────────────────────────
    function loadWorkflowList() {
        $('#workflow-selector').html('<option value="">Loading workflows...</option>');
        API.getWorkflows()
            .done(function (r) {
                if (r && r.records && r.records.length > 0) {
                    _workflowList = r.records.sort(function (a, b) { return a.workflowName.localeCompare(b.workflowName); });
                    const $dd = $('#workflow-selector');
                    $dd.empty().append('<option value="">Select a workflow...</option>');
                    _workflowList.forEach(function (wf) {
                        $dd.append(`<option value="${wf.workflowId}">${wf.workflowId} (${wf.workflowName})</option>`);
                    });
                    if (_currentWfId) $dd.val(_currentWfId);
                } else {
                    $('#workflow-selector').html('<option value="">No workflows found</option>');
                }
            })
            .fail(function (xhr) {
                const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load workflows';
                $('#workflow-selector').html(`<option value="">Error: ${msg}</option>`);
                showToast('Error', msg, 'danger');
            });
    }

    // ── Load DAG ───────────────────────────────────────────────
    function loadDAG(workflowId) {
        reset();
        $('#task-list').html('<div class="text-center py-3"><div class="spinner-border" role="status"></div><div class="mt-2">Loading tasks...</div></div>');
        $('#dag-canvas').html('<div class="text-center py-5"><div class="spinner-border" role="status"></div><div class="mt-2">Loading workflow DAG...</div></div>');

        const wf = _workflowList.find(function (w) { return w.workflowId === workflowId; });
        if (wf) {
            _setCurrentWorkflow(wf.workflowId, wf.workflowName);
            _fetchTasks(wf.workflowId);
        } else {
            API.getWorkflows({ workflowId })
                .done(function (r) {
                    if (r && r.records && r.records.length > 0) {
                        _setCurrentWorkflow(r.records[0].workflowId, r.records[0].workflowName);
                        _fetchTasks(r.records[0].workflowId);
                    } else {
                        $('#task-list').html('<div class="alert alert-warning">No workflow found.</div>');
                        $('#dag-canvas').html('<div class="alert alert-warning">Please select a valid workflow.</div>');
                    }
                })
                .fail(function (xhr) {
                    const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load workflow';
                    $('#task-list').html(`<div class="alert alert-danger">${msg}</div>`);
                    $('#dag-canvas').empty();
                    showToast('Error', msg, 'danger');
                });
        }
    }

    function _setCurrentWorkflow(id, name) {
        _currentWfId   = id;
        _currentWfName = name;
        $('#dag-workflow-title').text(`Editing: ${name} (${id})`);
        $('#dag-save-btn').prop('disabled', false);
        $('#dag-validate-btn').prop('disabled', false);
        $('#dag-auto-layout-btn').prop('disabled', false);
    }

    function _fetchTasks(workflowId) {
        API.getTasksByWorkflow(workflowId)
            .done(function (tasks) {
                $('#task-list').empty();
                $('#dag-canvas').empty();
                if (tasks && tasks.length > 0) {
                    tasks.sort(function (a, b) { return a.taskId.localeCompare(b.taskId); });
                    _taskMap = {};
                    tasks.forEach(function (t) { _taskMap[t.taskId] = t; });
                    _populateTaskList(tasks);
                    API.getDAG(workflowId)
                        .done(function (dagData) {
                            if (dagData && dagData.dag) { _renderDAG(dagData.dag); }
                            else { $('#dag-canvas').html('<div class="alert alert-warning">Failed to load DAG structure.</div>'); }
                        })
                        .fail(function (xhr) {
                            const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load DAG data';
                            $('#dag-canvas').html(`<div class="alert alert-danger">${msg}</div>`);
                        });
                } else {
                    $('#task-list').html('<div class="alert alert-info">No tasks found.</div>');
                    $('#dag-canvas').html('<div class="alert alert-info">No tasks to display.</div>');
                }
            })
            .fail(function (xhr) {
                const msg = (xhr.responseJSON && xhr.responseJSON.error) || 'Failed to load tasks';
                $('#task-list').html(`<div class="alert alert-danger">${msg}</div>`);
                $('#dag-canvas').empty();
            });
    }

    function _populateTaskList(tasks) {
        const $list = $('#task-list');
        $list.empty();
        tasks.forEach(function (task) {
            const color  = task.taskColor || _getColor(task.taskType);
            const muted  = task.enableFlag === 'Y' ? '' : 'text-muted';
            $list.append(`
                <div class="list-group-item list-group-item-action task-item ${muted} py-2"
                     draggable="true"
                     data-id="${task.taskId}" data-name="${task.taskName}"
                     data-type="${task.taskType}" data-color="${color}" data-enabled="${task.enableFlag}">
                    <div class="d-flex align-items-center">
                        <div class="task-color-indicator me-1" style="background-color:${color};"></div>
                        <div class="text-truncate">
                            <div class="small"><strong>${task.taskId}</strong></div>
                            <div class="small text-truncate">${task.taskName}</div>
                        </div>
                    </div>
                </div>`);
        });
    }

    // ── Render DAG ─────────────────────────────────────────────
    function _renderDAG(dagData) {
        _resetCanvas();
        _currentZoom = _zoomMap[_currentWfId] || 1.0;
        $('#dag-zoom-level').text(Math.round(_currentZoom * 100) + '%');

        if (!dagData.nodes || dagData.nodes.length === 0) {
            $('#dag-canvas').html('<div class="alert alert-info">No task nodes available.</div>');
            return;
        }

        dagData.nodes.forEach(function (node, index) {
            const task = _taskMap[node.id];
            if (task) {
                const x = node.x || (150 + (index % 5) * 250);
                const y = node.y || (80  + Math.floor(index / 5) * 150);
                _createNode(task, x, y);
            }
        });

        setTimeout(function () {
            if (dagData.edges && dagData.edges.length > 0) {
                dagData.edges.forEach(function (e) {
                    if (_taskMap[e.source] && _taskMap[e.target]) {
                        _jspInstance.connect({ source: e.source, target: e.target, type: 'basic' });
                    }
                });
            }
            _updatePredecessors();
            setTimeout(function () {
                _applyAutoLayout();
                // 移除原条件判断，因为布局内会调用 _autoFit
            }, 100);
        }, 100);
    }

    // FIX: 自定义拖拽处理 (替换 jsPlumb draggable，解决缩放时鼠标跑偏)
    function _makeDraggable(id) {
        const el = document.getElementById(id);
        if (!el) return;

        let startX = 0, startY = 0;
        let startLeft = 0, startTop = 0;
        let isDragging = false;
        let zoomAtStart = 1;

        function onMouseDown(e) {
            if (e.target.closest('.card-header')) return;
            e.preventDefault();
            startX = e.clientX;
            startY = e.clientY;
            const pos = _taskNodes[id].originalPosition;
            startLeft = pos.left;
            startTop = pos.top;
            zoomAtStart = _currentZoom;
            isDragging = true;
            document.addEventListener('mousemove', onMouseMove);
            document.addEventListener('mouseup', onMouseUp);
        }

        function onMouseMove(e) {
            if (!isDragging) return;
            const dx = (e.clientX - startX) / zoomAtStart;
            const dy = (e.clientY - startY) / zoomAtStart;
            let newLeft = startLeft + dx;
            let newTop = startTop + dy;

            const parent = el.parentElement;
            if (parent) {
                const nodeW = _taskNodes[id].originalPosition.width || 160;
                const nodeH = _taskNodes[id].originalPosition.height || 75;
                const maxLeft = (parent.clientWidth / _currentZoom) - nodeW;
                const maxTop  = (parent.clientHeight / _currentZoom) - nodeH;
                newLeft = Math.min(Math.max(0, newLeft), maxLeft);
                newTop  = Math.min(Math.max(0, newTop),  maxTop);
            }

            _taskNodes[id].originalPosition.left = newLeft;
            _taskNodes[id].originalPosition.top  = newTop;

            const z = _currentZoom;
            el.style.left = (newLeft * z) + 'px';
            el.style.top  = (newTop  * z) + 'px';

            _jspInstance.repaintEverything();
        }

        function onMouseUp(e) {
            isDragging = false;
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
        }

        el.addEventListener('mousedown', onMouseDown);
    }

    function _createNode(task, x, y) {
        const color   = task.taskColor || _getColor(task.taskType);
        const opacity = task.enableFlag === 'Y' ? '1' : '0.6';
        const z       = _currentZoom;

        const html = `
            <div class="card task-node" id="${task.taskId}"
                 style="position:absolute;left:${x*z}px;top:${y*z}px;opacity:${opacity};width:${160*z}px;height:${75*z}px;">
                <div class="card-header p-1" style="background-color:${color};color:white;position:relative;">
                    <div>
                        <strong>${task.taskType || 'Unknown'}</strong>
                        <button type="button" class="btn-close btn-close-white remove-task-btn" aria-label="Remove"
                                style="position:absolute;top:2px;right:2px;font-size:0.6rem;width:14px;height:14px;"></button>
                    </div>
                </div>
                <div class="card-body p-1" style="font-size:${_fontSize(z)}rem">
                    <div><strong>${task.taskName}</strong></div>
                    <div class="small text-truncate">${task.taskId}</div>
                </div>
            </div>`;

        $('#dag-canvas').append(html);
        _taskNodes[task.taskId] = { element: document.getElementById(task.taskId), task, originalPosition: { left: x, top: y, width: 160, height: 75 } };

        _makeDraggable(task.taskId);

        _jspInstance.makeSource(task.taskId, {
            filter        : '.card-header',
            anchor        : 'Right',
            connectorStyle: { stroke: '#5c96bc', strokeWidth: 2, outlineStroke: 'transparent', outlineWidth: 4 },
            connectionType: 'basic',
            maxConnections: -1
        });
        _jspInstance.makeTarget(task.taskId, { dropOptions: { hoverClass: 'dragHover' }, anchor: 'Left', allowLoopback: false });

        $(`#${task.taskId} .remove-task-btn`).on('click', function (e) {
            e.stopPropagation();
            _jspInstance.remove(task.taskId);
            delete _taskNodes[task.taskId];
            _updatePredecessors();
        });
    }

    // ── Layout ─────────────────────────────────────────────────
    function _applyAutoLayout() {
        const taskIds = Object.keys(_taskNodes);
        if (!taskIds.length) return;

        const connections = _jspInstance.getAllConnections();
        try {
            if (connections.length > 0) {
                _applyHierarchical(connections);
            } else {
                _applyGrid();
            }
        } catch (e) { _applyGrid(); }

        _jspInstance.repaintEverything();
        showToast('Layout', 'Applied layout to DAG', 'success');
        // 自动适配画布，消除滚动条
        _autoFit();
    }

    function _applyHierarchical(connections) {
        const succs = {}, preds = {};
        Object.keys(_taskNodes).forEach(function (id) { succs[id] = []; preds[id] = []; });
        connections.forEach(function (c) {
            if (succs[c.sourceId]) succs[c.sourceId].push(c.targetId);
            if (preds[c.targetId]) preds[c.targetId].push(c.sourceId);
        });

        const layers = [];
        const assigned = new Set();
        const first = Object.keys(_taskNodes).filter(function (id) { return preds[id].length === 0; });
        if (!first.length) first.push(Object.keys(_taskNodes)[0]);
        layers.push(first);
        first.forEach(function (id) { assigned.add(id); });

        let li = 0;
        while (assigned.size < Object.keys(_taskNodes).length) {
            const next = [];
            layers[li].forEach(function (nid) {
                succs[nid].forEach(function (sid) {
                    if (!assigned.has(sid) && preds[sid].every(function (p) { return assigned.has(p); })) {
                        next.push(sid); assigned.add(sid);
                    }
                });
            });
            if (!next.length) {
                const rem = Object.keys(_taskNodes).filter(function (id) { return !assigned.has(id); });
                rem.forEach(function (id) { next.push(id); assigned.add(id); });
            }
            if (next.length) layers.push(next);
            else break;
            li++;
        }

        for (let iter = 0; iter < 5; iter++) {
            let imp = false;
            for (let l = 0; l < layers.length; l++) {
                const layer = layers[l];
                for (let ni = 0; ni < layer.length; ni++) {
                    const node = layer.splice(ni, 1)[0];
                    let best = ni, bestX = Infinity;
                    for (let pos = 0; pos <= layer.length; pos++) {
                        layer.splice(pos, 0, node);
                        const x = _countCrossings(layers, l, succs);
                        if (x < bestX) { bestX = x; best = pos; imp = true; }
                        layer.splice(pos, 1);
                    }
                    layer.splice(best, 0, node);
                }
            }
            if (!imp) break;
        }

        const depth  = layers.length;
        const width  = Math.max(...layers.map(function (l) { return l.length; }));
        const nodeW  = 160, nodeH = 85;
        const hGap   = Math.max(200, 300 - depth * 10);
        const vGap   = Math.max(60,  120 - width * 5);
        const z      = _currentZoom;

        layers.forEach(function (layerNodes, li) {
            const lh    = layerNodes.length * nodeH + (layerNodes.length - 1) * vGap;
            const startY = Math.max(40, (600 - lh) / 2);
            layerNodes.sort();
            layerNodes.forEach(function (id, ni) {
                if (!_taskNodes[id] || !_taskNodes[id].element) return;
                const left = 40 + li * (nodeW + hGap);
                const top  = startY + ni * (nodeH + vGap);
                _taskNodes[id].originalPosition = { left, top, width: nodeW, height: nodeH };
                $(_taskNodes[id].element).css({ left: left*z+'px', top: top*z+'px', width: nodeW*z+'px', height: nodeH*z+'px', 'font-size': _fontSize(z)+'rem' });
            });
        });
    }

    function _countCrossings(layers, li, succs) {
        let total = 0;
        for (let i = 0; i < layers.length - 1; i++) {
            if (i !== li && i + 1 !== li) continue;
            const upper = layers[i], lower = layers[i + 1];
            const lpos  = {};
            lower.forEach(function (id, idx) { lpos[id] = idx; });
            for (let a = 0; a < upper.length; a++) {
                for (let b = a + 1; b < upper.length; b++) {
                    const sa = (succs[upper[a]] || []).filter(function (id) { return lpos.hasOwnProperty(id); });
                    const sb = (succs[upper[b]] || []).filter(function (id) { return lpos.hasOwnProperty(id); });
                    sa.forEach(function (x) { sb.forEach(function (y) { if (lpos[x] > lpos[y]) total++; }); });
                }
            }
        }
        return total;
    }

    function _applyGrid() {
        const taskIds = Object.keys(_taskNodes);
        const cols    = Math.min(5, Math.ceil(Math.sqrt(2 * taskIds.length)));
        const nodeW   = 160, nodeH = 85, hGap = 100, vGap = 60;
        const z       = _currentZoom;
        taskIds.forEach(function (id, index) {
            const col  = index % cols;
            const row  = Math.floor(index / cols);
            const left = 40 + col * (nodeW + hGap);
            const top  = 40 + row * (nodeH + vGap);
            _taskNodes[id].originalPosition = { left, top, width: nodeW, height: nodeH };
            $(_taskNodes[id].element).css({ left: left*z+'px', top: top*z+'px', width: nodeW*z+'px', height: nodeH*z+'px', 'font-size': _fontSize(z)+'rem' });
        });
    }

    // ── Zoom ───────────────────────────────────────────────────
    function _applyZoom(z) {
        _jspInstance.importDefaults({
            Endpoint: ['Dot', { radius: 4 * z }],
            Connector: ['Bezier', { curviness: 50 }],
            PaintStyle: { stroke: '#456', strokeWidth: 2 * z },
            HoverPaintStyle: { stroke: '#1e8151', strokeWidth: 3 * z }
        });
        _currentZoom = z;
        if (_currentWfId) _zoomMap[_currentWfId] = z;
        $('#dag-zoom-level').text(Math.round(z * 100) + '%');
        _jspInstance.setZoom(z);

        Object.keys(_taskNodes).forEach(function (id) {
            const $node = $(_taskNodes[id].element);
            const pos   = _taskNodes[id].originalPosition || { left: parseInt($node.css('left')), top: parseInt($node.css('top')), width: $node.width(), height: $node.height() };
            _taskNodes[id].originalPosition = pos;
            $node.css({ left: pos.left*z+'px', top: pos.top*z+'px', width: pos.width*z+'px', height: pos.height*z+'px', 'font-size': _fontSize(z)+'rem' });
            $node.find('.card-body').css({ 'font-size': _fontSize(z)+'rem' });
        });
        _jspInstance.repaintEverything();
    }

    function _autoFit() {
        if (!_currentWfId || !Object.keys(_taskNodes).length) return;
        let maxX = 0, maxY = 0, minX = Infinity, minY = Infinity;
        Object.values(_taskNodes).forEach(function (n) {
            if (!n.originalPosition) return;
            const p = n.originalPosition;
            if (p.left < minX) minX = p.left;
            if (p.top  < minY) minY = p.top;
            if (p.left + p.width  > maxX) maxX = p.left + p.width;
            if (p.top  + p.height > maxY) maxY = p.top  + p.height;
        });
        const tw = maxX - Math.min(0, minX) + 100;
        const th = maxY - Math.min(0, minY) + 100;
        const cw = $('.canvas-container .card-body').width()  - 20;
        const ch = $('.canvas-container .card-body').height() - 20;
        let target = Math.min(cw / tw, ch / th);
        target = Math.min(Math.max(Math.round(target * 10) / 10, 0.3), 1.0);
        if (Math.abs(target - _currentZoom) > 0.01) _applyZoom(target);
    }

    // ── Predecessors & Cycle ───────────────────────────────────
    function _updatePredecessors() {
        Object.values(_taskMap).forEach(function (t) { t.predecessors = ''; });
        const predMap = {};
        _jspInstance.getAllConnections().forEach(function (c) {
            if (!predMap[c.targetId]) predMap[c.targetId] = [];
            predMap[c.targetId].push(c.sourceId);
        });
        Object.keys(predMap).forEach(function (id) {
            if (_taskMap[id]) _taskMap[id].predecessors = predMap[id].join(',');
        });
        return _taskMap;
    }

    function _hasCycle() {
        const adj = {};
        Object.keys(_taskNodes).forEach(function (id) { adj[id] = []; });
        _jspInstance.getAllConnections().forEach(function (c) {
            if (!adj[c.sourceId]) adj[c.sourceId] = [];
            adj[c.sourceId].push(c.targetId);
        });
        const visited = {}, recStack = {};
        function dfs(n) {
            if (!visited[n]) {
                visited[n] = recStack[n] = true;
                for (const nb of (adj[n] || [])) {
                    if (!visited[nb] && dfs(nb)) return true;
                    if (recStack[nb]) return true;
                }
            }
            recStack[n] = false;
            return false;
        }
        return Object.keys(adj).some(dfs);
    }

    // ── Save / Validate ────────────────────────────────────────
    function _saveDAG() {
        if (!_currentWfId) { showToast('Error', 'No workflow selected', 'danger'); return; }
        const updated = _updatePredecessors();
        if (_hasCycle()) { showToast('Validation Error', 'Cannot save: Cycle detected', 'danger'); return; }

        const tasks = Object.keys(updated).map(function (id) {
            return { taskId: id, workflowId: _currentWfId, predecessors: updated[id].predecessors, lastUpdatedBy: getUsername() };
        });

        $('#dag-save-btn').prop('disabled', true).html('<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Saving...');

        if (!tasks.length) { _finalizeSave(false); return; }

        let done = 0, hasErr = false;
        tasks.forEach(function (task) {
            API.updateDAGTask(task)
                .done(function () { done++; if (done === tasks.length) _finalizeSave(hasErr); })
                .fail(function () { hasErr = true; done++; if (done === tasks.length) _finalizeSave(hasErr); });
        });
    }

    function _finalizeSave(hasErr) {
        $('#dag-save-btn').prop('disabled', false).text('Save DAG');
        showToast(hasErr ? 'Error' : 'Success', hasErr ? 'Some tasks could not be updated' : 'Workflow DAG saved successfully', hasErr ? 'danger' : 'success');
    }

    // ── Drag-and-drop from task list ───────────────────────────
    function _setupDragDrop() {
        $('#task-list').on('dragstart', '.task-item', function (e) {
            e.originalEvent.dataTransfer.setData('text/plain', JSON.stringify({
                id     : $(this).data('id'),
                name   : $(this).data('name'),
                type   : $(this).data('type'),
                color  : $(this).data('color'),
                enabled: $(this).data('enabled')
            }));
        });
        $('#dag-canvas').on('dragover', function (e) { e.preventDefault(); });
        $('#dag-canvas').on('drop', function (e) {
            e.preventDefault();
            try {
                const data = JSON.parse(e.originalEvent.dataTransfer.getData('text/plain'));
                if (document.getElementById(data.id)) {
                    showToast('Warning', `Task ${data.id} already exists in the canvas`, 'warning'); return;
                }
                const offset = $(this).offset();
                const x = e.originalEvent.pageX - offset.left;
                const y = e.originalEvent.pageY - offset.top;
                if (_taskMap[data.id]) {
                    if (!_taskMap[data.id].taskColor) _taskMap[data.id].taskColor = data.color;
                    _createNode(_taskMap[data.id], x, y);
                }
            } catch (err) { console.error('Drop error:', err); }
        });
    }

    // ── UI Controls ────────────────────────────────────────────
    function _setupZoomControls() {
        if ($('#dag-zoom-controls').length) return;
        $('.col-md-8.text-end').prepend(`
            <div id="dag-zoom-controls" class="btn-group me-2">
                <button class="btn btn-outline-secondary" id="dag-zoom-out-btn" title="Zoom Out"><i class="bi bi-zoom-out"></i></button>
                <button class="btn btn-outline-secondary" id="dag-zoom-level">100%</button>
                <button class="btn btn-outline-secondary" id="dag-zoom-in-btn" title="Zoom In"><i class="bi bi-zoom-in"></i></button>
                <button class="btn btn-outline-secondary" id="dag-zoom-reset-btn" title="Reset Zoom"><i class="bi bi-arrows-fullscreen"></i></button>
                <button class="btn btn-outline-secondary" id="dag-fit-screen-btn" title="Fit to Screen"><i class="bi bi-fullscreen"></i></button>
            </div>`);

        const step = 0.1, min = 0.3, max = 1.5;
        $('#dag-zoom-in-btn').on('click',    function () { if (_currentZoom < max) _applyZoom(Math.min(max, _currentZoom + step)); });
        $('#dag-zoom-out-btn').on('click',   function () { if (_currentZoom > min) _applyZoom(Math.max(min, _currentZoom - step)); });
        $('#dag-zoom-reset-btn').on('click', function () { _applyZoom(1.0); });
        $('#dag-fit-screen-btn').on('click', function () { _autoFit(); });
    }

    // ── Reset ──────────────────────────────────────────────────
    function _resetCanvas() {
        if (_jspInstance) _jspInstance.reset();
        $('#dag-canvas').empty();
    }

    function reset() {
        _currentZoom = 1.0;
        $('#dag-zoom-level').text('100%');
        _currentWfId = null; _currentWfName = null;
        _taskMap = {}; _taskNodes = {};
        $('#dag-workflow-title').text('Workflow DAG Editor');
        $('#dag-save-btn').prop('disabled', true);
        $('#dag-validate-btn').prop('disabled', true);
        $('#dag-auto-layout-btn').prop('disabled', true);
        _resetCanvas();
    }

    // ── Public init ────────────────────────────────────────────
    function initialize() {
        _initJsPlumb();
        $('#dag-canvas').css({ 'min-height': '600px', 'height': '600px' });
        $('.canvas-container .card-body').css({ 'overflow-x': 'auto', 'overflow-y': 'auto' });
        _setupDragDrop();
        _setupZoomControls();
        $('#dag-save-btn').prop('disabled', true);
        $('#dag-validate-btn').prop('disabled', true);
        $('#dag-auto-layout-btn').prop('disabled', true);

        $('#refresh-workflow-dag').on('click', function () {
            if (_taskTypeMap) loadTaskColors();
            loadWorkflowList();
            if (_currentWfId) loadDAG(_currentWfId);
            else reset();
        });
        $('#dag-save-btn').off('click').on('click',         _saveDAG);
        $('#dag-validate-btn').off('click').on('click',     function () {
            if (_hasCycle()) showToast('Validation Error', 'Cycle detected in workflow DAG', 'danger');
            else             showToast('Success', 'DAG validation successful – no cycles detected', 'success');
        });
        $('#dag-auto-layout-btn').off('click').on('click',  _applyAutoLayout);
        $('#workflow-selector').off('change').on('change',  function () {
            const id = $(this).val();
            if (id) loadDAG(id); else reset();
        });
    }

    function _fontSize(z) {
        return Math.max(0.5, Math.min(Math.round(z * 10) / 10, 1.5));
    }

    return {
        initialize,
        loadDAG,
        reset,
        loadTaskColors,
        loadWorkflowList
    };
})();


// ═══════════════════════════════════════════════════════════════
//  MONITOR DAG MODULE  (D3, circular nodes, read-only + refresh)
//  Used by dagviewer.html
// ═══════════════════════════════════════════════════════════════

const MonitorDAG = (function () {

    let _instanceId     = null;
    let _token          = null;
    let _dagData        = null;
    let _currentGraph   = null;
    let _currentZoom    = null;
    let _refreshTimer   = null;
    let _ctxNode        = null;

    const TERMINAL = ['COMPLETED', 'SKIPPED'];

    function _isTerminal(status) { return TERMINAL.includes((status || '').toUpperCase()); }

    function _edgeClass(srcStatus, tgtStatus) {
        const done = srcStatus && _isTerminal(srcStatus);
        return 'link ' + (done ? 'link-completed' : '');
    }

    function _load() {
        if (!_instanceId) { console.error('No instance ID'); return; }
        document.getElementById('graph').innerHTML = '';

        API.getInstanceDAG(_instanceId)
            .then(_onData)
            .catch(function () {
                API.getInstanceDAGAlt(_instanceId).then(_onData).catch(function (err) {
                    document.getElementById('graph').innerHTML = `<div style="padding:20px;color:red;">Failed to load: ${err.message}</div>`;
                });
            });
    }

    function _onData(data) {
        _dagData = {
            nodes      : [...data.dag.nodes],
            edges      : [...data.dag.edges],
            workflowId : data.workflowId,
            wfInstanceId: data.wfInstanceId,
            wfStatus   : (data.status || data.wfStatus || '').toUpperCase()
        };
        _render(_dagData);
    }

    function _refresh() {
        if (!_instanceId || !_dagData) return;
        if (_dagData.nodes.every(function (n) { return _isTerminal(n.status); })) {
            clearInterval(_refreshTimer); return;
        }

        fetch('/api/workflow/tasks/' + encodeURIComponent(_instanceId), {
            headers: { 'Authorization': 'Bearer ' + _token }
        }).then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        }).then(function (tasks) {
            let changed = false;
            _dagData.nodes.forEach(function (node) {
                const upd = tasks.find(function (t) { return t.taskInstanceId === node.id; });
                if (upd && (node.status !== upd.status || node.startDate !== upd.startDate || node.endDate !== upd.endDate)) {
                    node.status = upd.status; node.startDate = upd.startDate;
                    node.endDate = upd.endDate; node.errorMessage = upd.errorMessage;
                    changed = true;
                }
            });
            if (changed) { _updateNodeStatus(); _updateEdgeStatus(); }
            return fetch('/api/workflow/workflows?wfInstanceId=' + encodeURIComponent(_instanceId) + '&pageSize=1', {
                headers: { 'Authorization': 'Bearer ' + _token }
            });
        }).then(function (r) {
            if (!r || !r.ok) return null;
            return r.json();
        }).then(function (data) {
            var inst = data && data.records && data.records[0];
            if (inst && inst.status) {
                _dagData.wfStatus = inst.status.toUpperCase();
            }
        }).catch(function (err) { console.error('Refresh error:', err); });
    }

    function _updateNodeStatus() {
        if (!_currentGraph) return;
        d3.selectAll('.node circle').attr('class', function (d) {
            const node = _dagData.nodes.find(function (n) { return n.id === d.id; });
            return 'node-' + ((node ? node.status : 'waiting') || 'waiting').toLowerCase();
        });
    }

    function _updateEdgeStatus() {
        d3.selectAll('.link').each(function () {
            const edge   = d3.select(this);
            const srcId  = edge.attr('data-source');
            const tgtId  = edge.attr('data-target');
            const src    = _dagData.nodes.find(function (n) { return n.id === srcId || n.name === srcId; });
            const tgt    = _dagData.nodes.find(function (n) { return n.id === tgtId || n.name === tgtId; });
            const cls    = _edgeClass(src && src.status, tgt && tgt.status);
            edge.attr('class', cls);
            edge.attr('marker-end', cls.includes('link-completed') ? 'url(#arrow-completed)' : 'url(#arrow-default)');
        });
    }

    function _render(data) {
        if (!data || !data.nodes || !data.edges) {
            document.getElementById('graph').innerHTML = '<div style="padding:20px;color:red;">No valid DAG data found</div>';
            return;
        }

        const W = window.innerWidth, H = window.innerHeight;
        const margin = { top: 40, right: 150, bottom: 40, left: 150 };

        const svg = d3.select('#graph').append('svg').attr('width', W).attr('height', H);
        svg.append('rect').attr('width', W).attr('height', H).attr('fill', 'none').attr('pointer-events', 'all');
        const g = svg.append('g');

        const _succs = {}, _preds = {};
        data.nodes.forEach(function (n) { _succs[n.id] = []; _preds[n.id] = []; });
        data.edges.forEach(function (e) {
            if (_succs[e.source] !== undefined) _succs[e.source].push(e.target);
            if (_preds[e.target] !== undefined) _preds[e.target].push(e.source);
        });

        const _layers = [];
        const _assigned = new Set();
        const _first = data.nodes.map(function(n){return n.id;})
            .filter(function (id) { return _preds[id] && _preds[id].length === 0; });
        const _start = _first.length ? _first : [data.nodes[0].id];
        _layers.push(_start.slice());
        _start.forEach(function (id) { _assigned.add(id); });

        let _li = 0;
        while (_assigned.size < data.nodes.length) {
            const _next = [];
            _layers[_li].forEach(function (nid) {
                (_succs[nid] || []).forEach(function (sid) {
                    if (!_assigned.has(sid) &&
                        (_preds[sid] || []).every(function (p) { return _assigned.has(p); })) {
                        _next.push(sid); _assigned.add(sid);
                    }
                });
            });
            if (!_next.length) {
                data.nodes.forEach(function (n) {
                    if (!_assigned.has(n.id)) { _next.push(n.id); _assigned.add(n.id); }
                });
            }
            if (_next.length) _layers.push(_next);
            else break;
            _li++;
        }

        function _monitorCountCrossings(ls, layerIdx) {
            let total = 0;
            for (let i = 0; i < ls.length - 1; i++) {
                if (i !== layerIdx && i + 1 !== layerIdx) continue;
                const upper = ls[i], lower = ls[i + 1];
                const lpos = {};
                lower.forEach(function (id, idx) { lpos[id] = idx; });
                for (let a = 0; a < upper.length; a++) {
                    for (let b = a + 1; b < upper.length; b++) {
                        const sa = (_succs[upper[a]] || []).filter(function(id){return lpos.hasOwnProperty(id);});
                        const sb = (_succs[upper[b]] || []).filter(function(id){return lpos.hasOwnProperty(id);});
                        sa.forEach(function(x){ sb.forEach(function(y){ if(lpos[x]>lpos[y]) total++; }); });
                    }
                }
            }
            return total;
        }
        for (let _iter = 0; _iter < 5; _iter++) {
            let _imp = false;
            for (let _l = 0; _l < _layers.length; _l++) {
                const _layer = _layers[_l];
                for (let _ni = 0; _ni < _layer.length; _ni++) {
                    const _node = _layer.splice(_ni, 1)[0];
                    let _best = _ni, _bestX = Infinity;
                    for (let _pos = 0; _pos <= _layer.length; _pos++) {
                        _layer.splice(_pos, 0, _node);
                        const _x = _monitorCountCrossings(_layers, _l);
                        if (_x < _bestX) { _bestX = _x; _best = _pos; _imp = true; }
                        _layer.splice(_pos, 1);
                    }
                    _layer.splice(_best, 0, _node);
                }
            }
            if (!_imp) break;
        }

        const _r    = 30;
        const nodeW = 160, nodeH = 85;
        const _depth = _layers.length;
        const _width = Math.max(..._layers.map(function(l){return l.length;}));
        const hGap  = Math.max(200, 300 - _depth * 10);
        const vGap  = Math.max(60,  120 - _width * 5);
        const CANVAS_H = 600;

        _layers.forEach(function (layerIds, li) {
            const lh     = layerIds.length * nodeH + (layerIds.length - 1) * vGap;
            const startY = Math.max(40, (CANVAS_H - lh) / 2);
            layerIds.sort();
            layerIds.forEach(function (id, ni) {
                const node = data.nodes.find(function (n) { return n.id === id; });
                if (!node) return;
                node.fx = margin.left + li * (nodeW + hGap);
                node.fy = startY + ni * (nodeH + vGap) + nodeH / 2;
            });
        });

        const defs = svg.append('defs');
        [{ id: 'arrow-default', fill: '#999' }, { id: 'arrow-completed', fill: '#4CAF50' }].forEach(function (m) {
            defs.append('marker').attr('id', m.id).attr('viewBox', '0 -5 10 10').attr('refX', 8).attr('refY', 0)
                .attr('markerWidth', 6).attr('markerHeight', 6).attr('orient', 'auto')
                .append('path').attr('d', 'M0,-5L10,0L0,5').attr('fill', m.fill);
        });

        const lineGen = d3.line().x(function (d) { return d.x; }).y(function (d) { return d.y; }).curve(d3.curveBasis);
        const procEdges = data.edges.map(function (e) {
            const src = data.nodes.find(function (n) { return n.id === e.source; });
            const tgt = data.nodes.find(function (n) { return n.id === e.target; });
            if (!src || !tgt) return null;
            const cls = _edgeClass(src.status, tgt.status);
            const path = [
                { x: src.fx + 30, y: src.fy },
                { x: src.fx + (tgt.fx - src.fx) * 0.3, y: src.fy },
                { x: src.fx + (tgt.fx - src.fx) * 0.7, y: tgt.fy },
                { x: tgt.fx - 30, y: tgt.fy }
            ];
            return { id: `${src.id}-${tgt.id}`, srcName: src.name||src.id, tgtName: tgt.name||tgt.id, path, d: lineGen(path), cls };
        }).filter(Boolean);

        g.selectAll('.link').data(procEdges).enter().append('path')
            .attr('class',         function (d) { return d.cls; })
            .attr('d',             function (d) { return d.d; })
            .attr('id',            function (d) { return 'path-' + d.id; })
            .attr('data-source',   function (d) { return d.srcName; })
            .attr('data-target',   function (d) { return d.tgtName; })
            .attr('marker-end',    function (d) { return d.cls.includes('link-completed') ? 'url(#arrow-completed)' : 'url(#arrow-default)'; });

        const tooltip = d3.select('#tooltip');

        const nodes = g.selectAll('.node').data(data.nodes).enter().append('g')
            .attr('class', 'node')
            .attr('transform', function (d) { return `translate(${d.fx},${d.fy})`; })
            .call(d3.drag()
                .on('start', function (evt, d) { d3.select(this).raise(); })
                .on('drag',  function (evt, d) {
                    d.fx = evt.x; d.fy = evt.y;
                    d3.select(this).attr('transform', `translate(${d.fx},${d.fy})`);
                    procEdges.forEach(function (edge) {
                        if (edge.srcName === (d.name||d.id) || edge.tgtName === (d.name||d.id)) {
                            const src = data.nodes.find(function (n) { return (n.name||n.id) === edge.srcName; });
                            const tgt = data.nodes.find(function (n) { return (n.name||n.id) === edge.tgtName; });
                            if (src && tgt) {
                                const np = [
                                    { x: src.fx + 30, y: src.fy },
                                    { x: src.fx + (tgt.fx-src.fx)*0.3, y: src.fy },
                                    { x: src.fx + (tgt.fx-src.fx)*0.7, y: tgt.fy },
                                    { x: tgt.fx - 30, y: tgt.fy }
                                ];
                                edge.d = lineGen(np);
                                d3.select('#path-' + edge.id).attr('d', edge.d);
                            }
                        }
                    });
                })
                .on('end',   function () {}));

        nodes.append('circle').attr('r', 30)
            .attr('class', function (d) { return 'node-' + ((d.status||'waiting').toLowerCase()); })
            .on('mouseover', function (evt, d) {
                const dur = _calcDuration(d.startDate, d.endDate);
                let html  = `<strong>ID:</strong> ${d.id||'N/A'}<br><strong>Name:</strong> ${d.name||'N/A'}<br><strong>Status:</strong> ${d.status||'N/A'}<br>`;
                if (d.startDate) html += `<strong>Start:</strong> ${new Date(d.startDate).toLocaleString()}<br>`;
                if (d.endDate)   html += `<strong>End:</strong>   ${new Date(d.endDate).toLocaleString()}<br>`;
                html += `<strong>Duration:</strong> ${dur}<br>`;
                if (d.errorMessage) html += `<strong>Error:</strong> ${d.errorMessage}<br>`;
                tooltip.html(html).style('left', (evt.pageX+10)+'px').style('top', (evt.pageY-20)+'px').style('opacity', 1);
            })
            .on('mouseout', function () { tooltip.style('opacity', 0); })
            .on('contextmenu', function (evt, d) {
                evt.preventDefault();
                tooltip.style('opacity', 0);
                _hideContextMenu();
                _showContextMenu(evt, d);
            });

        nodes.append('text').attr('class', 'node-text').attr('dy', -38).text(function (d) { return d.name||d.id; });

        const legend = svg.append('g').attr('transform', `translate(${W-140},20)`);
        legend.append('rect').attr('width', 130).attr('height', 170).attr('fill', 'white').attr('stroke', '#ccc');
        [
            { name:'Waiting',   cls:'node-waiting'   },
            { name:'Running',   cls:'node-running'   },
            { name:'Completed', cls:'node-completed' },
            { name:'Error',     cls:'node-error'     },
            { name:'Timeout',   cls:'node-timeout'   },
            { name:'Canceled',  cls:'node-canceled'  },
            { name:'Skipped',   cls:'node-skipped'   }
        ].forEach(function (s, i) {
            const li = legend.append('g').attr('transform', `translate(10,${i*20+20})`);
            li.append('circle').attr('r', 7).attr('class', s.cls);
            li.append('text').attr('x', 15).attr('y', 5).text(s.name).style('font-size', '12px');
        });

        const zoom = d3.zoom().scaleExtent([0.1, 4])
            .on('zoom', function (evt) { g.attr('transform', evt.transform); });
        svg.call(zoom);
        _currentZoom = zoom; _currentGraph = svg;

        const bounds = g.node().getBBox();
        const scale  = Math.min(W / (bounds.width + margin.left + margin.right), H / (bounds.height + margin.top + margin.bottom), 1);
        svg.call(zoom.transform, d3.zoomIdentity
            .translate((W - (bounds.width + margin.left + margin.right) * scale) / 2,
                       (H - (bounds.height + margin.top + margin.bottom) * scale) / 2)
            .scale(scale));
    }

    const CANCELABLE = ['RUNNING'];
    const RETRYABLE  = ['ERROR', 'TIMEOUT', 'CANCELED'];
    const SKIPPABLE  = ['WAITING', 'TIMEOUT', 'ERROR', 'CANCELED'];

    function _showContextMenu(evt, d) {
        const status         = (d.status || '').toUpperCase();
        const canCancel = CANCELABLE.includes(status);
        const wfStatus  = (_dagData && _dagData.wfStatus) || '';
        const wfAllowsTaskAction = ['RUNNING', 'ERROR'].includes(wfStatus.toUpperCase());
        const canRetry  = wfAllowsTaskAction && RETRYABLE.includes(status);
        const canSkip   = wfAllowsTaskAction && SKIPPABLE.includes(status);

        if (!canCancel && !canRetry && !canSkip) return;

        _ctxNode = d;

        const menu         = document.getElementById('dag-context-menu');
        const headerLabel  = document.getElementById('ctx-header-label');
        const cancelBtn    = document.getElementById('ctx-cancel-btn');
        const retryBtn     = document.getElementById('ctx-retry-btn');
        const skipBtn      = document.getElementById('ctx-skip-btn');
        const cancelWfBtn  = document.getElementById('ctx-cancel-wf-btn');
        const recoverWfBtn = document.getElementById('ctx-recover-wf-btn');

        headerLabel.textContent = (d.name || d.id) + '  [' + (d.status || '') + ']';

        cancelBtn.style.display    = canCancel ? 'flex' : 'none';
        retryBtn.style.display     = canRetry  ? 'flex' : 'none';
        skipBtn.style.display      = canSkip   ? 'flex' : 'none';
        cancelWfBtn.style.display  = 'none';
        recoverWfBtn.style.display = 'none';
        cancelBtn.disabled  = false;
        retryBtn.disabled   = false;
        skipBtn.disabled    = false;

        const itemCount = [canCancel, canRetry, canSkip].filter(Boolean).length;
        _positionAndShow(menu, evt, itemCount);
    }

    function _showWorkflowContextMenu(evt) {
        const wfStatus = (_dagData && _dagData.wfStatus) || '';
        if (wfStatus !== 'RUNNING') return;

        _ctxNode = null;

        const menu         = document.getElementById('dag-context-menu');
        const headerLabel  = document.getElementById('ctx-header-label');
        const cancelBtn    = document.getElementById('ctx-cancel-btn');
        const retryBtn     = document.getElementById('ctx-retry-btn');
        const cancelWfBtn  = document.getElementById('ctx-cancel-wf-btn');
        const recoverWfBtn = document.getElementById('ctx-recover-wf-btn');

        headerLabel.textContent = (_dagData.workflowId || _dagData.wfInstanceId || '') + '  [' + wfStatus + ']';

        cancelBtn.style.display    = 'none';
        retryBtn.style.display     = 'none';
        if (document.getElementById('ctx-skip-btn'))
            document.getElementById('ctx-skip-btn').style.display = 'none';
        cancelWfBtn.style.display  = 'flex';
        recoverWfBtn.style.display = 'flex';
        cancelWfBtn.disabled   = false;
        recoverWfBtn.disabled  = false;

        _positionAndShow(menu, evt, 2);
    }

    function _positionAndShow(menu, evt, itemCount) {
        const menuW = 210;
        const menuH = 52 + itemCount * 32;
        const x = Math.min(evt.clientX, window.innerWidth  - menuW - 8);
        const y = Math.min(evt.clientY, window.innerHeight - menuH - 8);
        menu.style.left = x + 'px';
        menu.style.top  = y + 'px';
        menu.classList.add('visible');
    }

    function _hideContextMenu() {
        const menu = document.getElementById('dag-context-menu');
        if (menu) menu.classList.remove('visible');
        _ctxNode = null;
    }

    function _dagToast(msg, type) {
        const container = document.getElementById('dag-toast');
        if (!container) return;
        const el = document.createElement('div');
        el.className = 'dag-toast-msg dag-toast-' + (type || 'info');
        el.textContent = msg;
        container.appendChild(el);
        requestAnimationFrame(function () {
            requestAnimationFrame(function () { el.classList.add('show'); });
        });
        setTimeout(function () {
            el.classList.remove('show');
            setTimeout(function () { el.remove(); }, 300);
        }, 3000);
    }

    function _doAction(action, id, label) {
        ['ctx-cancel-btn','ctx-retry-btn','ctx-skip-btn','ctx-cancel-wf-btn','ctx-recover-wf-btn'].forEach(function(bid) {
            const b = document.getElementById(bid);
            if (b) b.disabled = true;
        });

        _hideContextMenu();

        let url, body;
        if (action === 'cancel-task') {
            url  = '/api/workflow/canceltask';
            body = JSON.stringify({ taskInstanceId: id });
            _dagToast('Cancelling task ' + id + '…', 'info');
        } else if (action === 'retry-task') {
            url  = '/api/workflow/retry';
            body = JSON.stringify({ taskInstanceId: id });
            _dagToast('Retrying task ' + id + '…', 'info');
        } else if (action === 'cancel-wf') {
            url  = '/api/workflow/cancel';
            body = JSON.stringify({ instanceId: id });
            _dagToast('Cancelling workflow ' + id + '…', 'info');
        } else if (action === 'recover-wf') {
            url  = '/api/workflow/recover';
            body = JSON.stringify({ instanceId: id });
            _dagToast('Recovering workflow ' + id + '…', 'info');
        } else if (action === 'skip-task') {
            url  = '/api/workflow/skip';
            body = JSON.stringify({ taskInstanceId: id });
            _dagToast('Skipping task ' + id + '…', 'info');
        } else {
            return;
        }

        fetch(url, {
            method : 'POST',
            headers: {
                'Content-Type'  : 'application/json',
                'Authorization' : 'Bearer ' + _token
            },
            body: body
        })
        .then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.json();
        })
        .then(function (resp) {
            if (resp && resp.status === 'success') {
                _dagToast(label + ' succeeded', 'success');
                _refresh();
            } else {
                _dagToast((resp && resp.error) || (label + ' failed'), 'error');
            }
        })
        .catch(function (err) {
            _dagToast(label + ' failed: ' + err.message, 'error');
        });
    }

    function _setupContextMenu() {
        const cancelBtn = document.getElementById('ctx-cancel-btn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', function () {
                if (!_ctxNode) return;
                _doAction('cancel-task', _ctxNode.id, 'Cancel task');
            });
        }
        const retryBtn = document.getElementById('ctx-retry-btn');
        if (retryBtn) {
            retryBtn.addEventListener('click', function () {
                if (!_ctxNode) return;
                _doAction('retry-task', _ctxNode.id, 'Retry task');
            });
        }
        const skipBtn = document.getElementById('ctx-skip-btn');
        if (skipBtn) {
            skipBtn.addEventListener('click', function () {
                if (!_ctxNode) return;
                _doAction('skip-task', _ctxNode.id, 'Skip task');
            });
        }

        const cancelWfBtn = document.getElementById('ctx-cancel-wf-btn');
        if (cancelWfBtn) {
            cancelWfBtn.addEventListener('click', function () {
                if (!_dagData) return;
                _doAction('cancel-wf', _dagData.wfInstanceId, 'Cancel workflow');
            });
        }
        const recoverWfBtn = document.getElementById('ctx-recover-wf-btn');
        if (recoverWfBtn) {
            recoverWfBtn.addEventListener('click', function () {
                if (!_dagData) return;
                _doAction('recover-wf', _dagData.wfInstanceId, 'Recover workflow');
            });
        }

        document.addEventListener('click', function (evt) {
            const menu = document.getElementById('dag-context-menu');
            if (menu && !menu.contains(evt.target)) _hideContextMenu();
        });

        document.addEventListener('contextmenu', function (evt) {
            const onNode = evt.target.closest && evt.target.closest('.node');
            if (onNode) {
                return;
            }
            evt.preventDefault();
            _hideContextMenu();
            _showWorkflowContextMenu(evt);
        });
    }

    function _calcDuration(start, end) {
        if (!start || !end) return 'N/A';
        try {
            const ms  = new Date(end) - new Date(start);
            if (ms < 0) return 'N/A';
            const s = Math.floor(ms/1000)%60;
            const m = Math.floor(ms/60000)%60;
            const h = Math.floor(ms/3600000);
            return h > 0 ? `${h}h ${m}m ${s}s` : m > 0 ? `${m}m ${s}s` : `${s}s`;
        } catch (e) { return 'N/A'; }
    }

    function init() {
        _token      = localStorage.getItem('auth_token');
        _instanceId = localStorage.getItem('selectedWfInstanceId');

        if (!_instanceId || !_token) { alert('Missing required parameters: instanceId and token'); return; }

        const origOpen = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function () {
            origOpen.apply(this, arguments);
            this.setRequestHeader('Authorization', 'Bearer ' + _token);
        };

        _setupContextMenu();
        _load();
        _refreshTimer = setInterval(_refresh, 2000);

        window.addEventListener('resize', function () {
            if (_dagData) { document.getElementById('graph').innerHTML = ''; _render(_dagData); }
        });
        window.addEventListener('beforeunload', function () { clearInterval(_refreshTimer); });
    }

    return { init };
})();