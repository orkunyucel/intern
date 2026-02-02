/**
 * Spring AI RAG Visualization - Frontend Application
 * Professional Edition
 *
 * Features:
 * - WebSocket connection (STOMP)
 * - Real-time node animation with detailed step information
 * - Event logging with timestamps
 * - Performance metrics tracking
 * - Step detail panels
 */

// ============================================================================
// State Management
// ============================================================================

const state = {
    stompClient: null,
    isConnected: false,
    currentRequestId: null,
    stepData: {},
    metrics: {
        embedding: null,
        search: null,
        llm: null,
        docs: null
    },
    stats: {
        totalQueries: 0,
        successCount: 0,
        durations: []
    }
};

// ============================================================================
// DOM Elements
// ============================================================================

const elements = {
    connectionStatus: document.getElementById('connection-status'),
    questionInput: document.getElementById('question-input'),
    submitBtn: document.getElementById('submit-btn'),
    loadSampleBtn: document.getElementById('load-sample-btn'),
    clearBtn: document.getElementById('clear-btn'),
    requestId: document.getElementById('current-request-id'),
    eventLogContent: document.getElementById('event-log-content'),
    responseContent: document.getElementById('response-content'),
    stepDetailsContent: document.getElementById('step-details-content'),
    totalQueries: document.getElementById('total-queries'),
    avgDuration: document.getElementById('avg-duration'),
    successRate: document.getElementById('success-rate')
};

// ============================================================================
// WebSocket Connection
// ============================================================================

function connectWebSocket() {
    console.log('[WS] Connecting to WebSocket...');

    const socket = new SockJS('/ws');
    state.stompClient = Stomp.over(socket);

    // Disable debug logging
    state.stompClient.debug = null;

    state.stompClient.connect({}, onConnected, onError);
}

function onConnected() {
    console.log('[WS] Connected successfully');
    state.isConnected = true;
    updateConnectionStatus(true);

    // Subscribe to RAG events
    state.stompClient.subscribe('/topic/rag-events', onRagEvent);
}

function onError(error) {
    console.error('[WS] Connection error:', error);
    state.isConnected = false;
    updateConnectionStatus(false);

    // Retry connection after 3 seconds
    setTimeout(connectWebSocket, 3000);
}

function updateConnectionStatus(connected) {
    const badge = elements.connectionStatus;
    const textEl = badge.querySelector('.status-text');

    if (connected) {
        badge.classList.add('connected');
        textEl.textContent = 'Connected';
    } else {
        badge.classList.remove('connected');
        textEl.textContent = 'Disconnected';
    }
}

// ============================================================================
// RAG Event Handling
// ============================================================================

function onRagEvent(message) {
    const event = JSON.parse(message.body);
    console.log('[RAG Event]', event);

    // Update request ID
    if (event.requestId && event.requestId !== state.currentRequestId) {
        state.currentRequestId = event.requestId;
        elements.requestId.textContent = event.requestId;
    }

    // Store step data for details panel
    storeStepData(event);

    // Update node status
    updateNodeStatus(event.node, event.status);

    // Update node details
    updateNodeDetails(event);

    // Add to event log
    addLogEntry(event);

    // Update metrics
    if (event.durationMs) {
        updateMetrics(event.node, event.durationMs, event.data);
    }

    // Activate connection line
    activateConnection(event.node, event.status);
}

// ============================================================================
// Step Data Storage
// ============================================================================

function storeStepData(event) {
    const nodeName = event.node;

    if (!state.stepData[nodeName]) {
        state.stepData[nodeName] = {};
    }

    state.stepData[nodeName] = {
        status: event.status,
        message: event.message,
        durationMs: event.durationMs,
        data: event.data,
        timestamp: event.timestamp,
        error: event.error
    };
}

// ============================================================================
// Node Animation
// ============================================================================

function updateNodeStatus(nodeName, status) {
    const node = document.getElementById(`node-${nodeName}`);
    if (!node) return;

    // Update data-status attribute
    node.dataset.status = status.toLowerCase();

    // Animate node
    if (status === 'COMPLETED') {
        animateNodeCompletion(node);
    } else if (status === 'ERROR') {
        animateNodeError(node);
    }
}

function animateNodeCompletion(node) {
    node.style.transition = 'transform 0.2s ease-out';
    node.style.transform = 'scale(1.02)';

    setTimeout(() => {
        node.style.transform = 'scale(1)';
    }, 200);
}

function animateNodeError(node) {
    node.style.animation = 'shake 0.4s ease-in-out';
    setTimeout(() => {
        node.style.animation = '';
    }, 400);
}

// Add shake animation
const shakeStyle = document.createElement('style');
shakeStyle.textContent = `
@keyframes shake {
    0%, 100% { transform: translateX(0); }
    20% { transform: translateX(-5px); }
    40% { transform: translateX(5px); }
    60% { transform: translateX(-5px); }
    80% { transform: translateX(5px); }
}
`;
document.head.appendChild(shakeStyle);

// ============================================================================
// Node Details Update
// ============================================================================

function updateNodeDetails(event) {
    const detailsEl = document.getElementById(`details-${event.node}`);
    if (!detailsEl) return;

    let html = '';

    switch (event.node) {
        case 'USER_INPUT':
            html = `
                <div class="detail-item">
                    <span class="detail-label">Query:</span>
                    <span class="detail-value">${escapeHtml(event.message.replace('User question received: ', ''))}</span>
                </div>
            `;
            break;

        case 'EMBEDDING_GENERATION':
            if (event.status === 'PROCESSING') {
                html = `<p>Generating embedding vector using text-embedding-004...</p>`;
            } else if (event.status === 'COMPLETED') {
                const dims = event.data?.dimensions || '-';
                html = `
                    <div class="detail-item">
                        <span class="detail-label">Dimensions:</span>
                        <span class="detail-value">${dims}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Duration:</span>
                        <span class="detail-value">${event.durationMs}ms</span>
                    </div>
                `;
            }
            break;

        case 'VECTOR_SEARCH':
            if (event.status === 'PROCESSING') {
                html = `<p>Searching Qdrant vector database...</p>`;
            } else if (event.status === 'COMPLETED') {
                const docCount = event.data?.documentCount || 0;
                html = `
                    <div class="detail-item">
                        <span class="detail-label">Documents Found:</span>
                        <span class="detail-value">${docCount}</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Threshold:</span>
                        <span class="detail-value">0.70</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Duration:</span>
                        <span class="detail-value">${event.durationMs}ms</span>
                    </div>
                `;
            }
            break;

        case 'RETRIEVED_DOCUMENTS':
            html = `
                <div class="detail-item">
                    <span class="detail-value">${escapeHtml(event.message)}</span>
                </div>
            `;
            break;

        case 'LLM_CALL':
            if (event.status === 'PROCESSING') {
                html = `<p>Calling Gemini 2.5 Flash with context...</p>`;
            } else if (event.status === 'COMPLETED') {
                const responseLen = event.data?.responseLength || '-';
                html = `
                    <div class="detail-item">
                        <span class="detail-label">Response Length:</span>
                        <span class="detail-value">${responseLen} chars</span>
                    </div>
                    <div class="detail-item">
                        <span class="detail-label">Duration:</span>
                        <span class="detail-value">${event.durationMs}ms</span>
                    </div>
                `;
            }
            break;

        case 'RESPONSE_GENERATION':
            html = `
                <div class="detail-item">
                    <span class="detail-value">${escapeHtml(event.message)}</span>
                </div>
            `;
            break;

        default:
            if (event.error) {
                html = `<p style="color: var(--color-error);">${escapeHtml(event.error)}</p>`;
            } else {
                html = `<p>${escapeHtml(event.message)}</p>`;
            }
    }

    detailsEl.innerHTML = html;

    // Make node clickable to show in right panel
    const node = document.getElementById(`node-${event.node}`);
    if (node && event.status === 'COMPLETED') {
        node.style.cursor = 'pointer';
        node.onclick = () => showStepDetails(event.node);
    }
}

// ============================================================================
// Step Details Panel (Right Side)
// ============================================================================

function showStepDetails(nodeName) {
    const data = state.stepData[nodeName];
    if (!data) return;

    let html = `<div class="step-detail-item">
        <div class="step-detail-title">${nodeName.replace(/_/g, ' ')}</div>
        <div class="step-detail-content">${escapeHtml(data.message)}</div>
    </div>`;

    if (data.durationMs) {
        html += `<div class="step-detail-item">
            <div class="step-detail-title">Duration</div>
            <div class="step-detail-content">${data.durationMs}ms</div>
        </div>`;
    }

    if (data.data && Object.keys(data.data).length > 0) {
        html += `<div class="step-detail-item">
            <div class="step-detail-title">Data</div>
            <div class="step-detail-content">${JSON.stringify(data.data, null, 2)}</div>
        </div>`;
    }

    if (data.error) {
        html += `<div class="step-detail-item">
            <div class="step-detail-title" style="color: var(--color-error);">Error</div>
            <div class="step-detail-content" style="color: var(--color-error);">${escapeHtml(data.error)}</div>
        </div>`;
    }

    elements.stepDetailsContent.innerHTML = html;
}

// ============================================================================
// Connection Line Animation
// ============================================================================

function activateConnection(nodeName, status) {
    const nodeOrder = [
        'USER_INPUT',
        'EMBEDDING_GENERATION',
        'VECTOR_SEARCH',
        'RETRIEVED_DOCUMENTS',
        'LLM_CALL',
        'RESPONSE_GENERATION',
        'COMPLETED'
    ];

    const index = nodeOrder.indexOf(nodeName);

    if (index > 0 && status === 'COMPLETED') {
        const line = document.getElementById(`line-${index}`);
        if (line) {
            line.classList.add('active');
        }
    }
}

// ============================================================================
// Event Log
// ============================================================================

function addLogEntry(event) {
    // Remove empty message if exists
    const empty = elements.eventLogContent.querySelector('.log-empty');
    if (empty) {
        empty.remove();
    }

    const entry = document.createElement('div');
    entry.className = `log-entry ${event.status.toLowerCase()}`;

    const timestamp = new Date(event.timestamp).toLocaleTimeString();
    const duration = event.durationMs ? ` (${event.durationMs}ms)` : '';

    entry.innerHTML = `
        <span class="log-timestamp">${timestamp}</span>
        <span class="log-message">
            <strong>${event.node}:</strong> ${escapeHtml(event.message)}
            ${duration ? `<span class="log-duration">${duration}</span>` : ''}
        </span>
    `;

    if (event.error) {
        entry.innerHTML += `<span style="color: var(--color-error); display: block; margin-top: 4px;">${escapeHtml(event.error)}</span>`;
    }

    elements.eventLogContent.insertBefore(entry, elements.eventLogContent.firstChild);

    // Limit log entries to 30
    while (elements.eventLogContent.children.length > 30) {
        elements.eventLogContent.removeChild(elements.eventLogContent.lastChild);
    }
}

// ============================================================================
// Metrics Update
// ============================================================================

function updateMetrics(node, duration, data) {
    switch (node) {
        case 'EMBEDDING_GENERATION':
            state.metrics.embedding = duration;
            document.getElementById('metric-embedding').textContent = `${duration}ms`;
            break;
        case 'VECTOR_SEARCH':
            state.metrics.search = duration;
            document.getElementById('metric-search').textContent = `${duration}ms`;
            if (data && data.documentCount !== undefined) {
                state.metrics.docs = data.documentCount;
                document.getElementById('metric-docs').textContent = data.documentCount;
            }
            break;
        case 'LLM_CALL':
            state.metrics.llm = duration;
            document.getElementById('metric-llm').textContent = `${duration}ms`;
            break;
    }
}

// ============================================================================
// Form Submission
// ============================================================================

document.getElementById('query-form').addEventListener('submit', async (e) => {
    e.preventDefault();

    const question = elements.questionInput.value.trim();
    if (!question) return;

    // Disable button
    elements.submitBtn.disabled = true;
    elements.submitBtn.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spinning">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <span>Processing...</span>
    `;

    // Add spinning animation
    const spinStyle = document.createElement('style');
    spinStyle.id = 'spin-style';
    spinStyle.textContent = `.spinning { animation: spin 1s linear infinite; }`;
    document.head.appendChild(spinStyle);

    // Clear previous results
    clearVisualization(false);

    try {
        const response = await fetch('/api/rag/query-visual', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ question })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const data = await response.json();

        // Display response
        elements.responseContent.innerHTML = `<div class="response-text">${escapeHtml(data.answer)}</div>`;

        // Update total duration
        document.getElementById('metric-total').textContent = `${data.durationMs}ms`;

        // Update stats
        state.stats.totalQueries++;
        state.stats.successCount++;
        state.stats.durations.push(data.durationMs);
        updateStats();

    } catch (error) {
        console.error('[Error] Query failed:', error);

        elements.responseContent.innerHTML = `
            <div style="color: var(--color-error); text-align: center; padding: 20px;">
                <strong>Error:</strong> ${escapeHtml(error.message)}
            </div>
        `;

        state.stats.totalQueries++;
        updateStats();
    } finally {
        // Re-enable button
        elements.submitBtn.disabled = false;
        elements.submitBtn.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M5 12h14"/>
                <path d="m12 5 7 7-7 7"/>
            </svg>
            <span>Run Pipeline</span>
        `;

        // Remove spinning style
        const spinStyleEl = document.getElementById('spin-style');
        if (spinStyleEl) spinStyleEl.remove();
    }
});

// ============================================================================
// Load Sample Data
// ============================================================================

elements.loadSampleBtn.addEventListener('click', async () => {
    elements.loadSampleBtn.disabled = true;
    const originalHTML = elements.loadSampleBtn.innerHTML;
    elements.loadSampleBtn.innerHTML = `
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="spinning">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        Loading...
    `;

    try {
        const response = await fetch('/api/rag/index/sample', { method: 'POST' });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        // Show success notification
        showNotification('Sample data loaded! Try: "How many vacation days do I get?", "What is the learning budget?", "How does the performance review work?"', 'success');

    } catch (error) {
        console.error('[Error] Failed to load sample data:', error);
        showNotification('Failed to load sample data: ' + error.message, 'error');
    } finally {
        elements.loadSampleBtn.disabled = false;
        elements.loadSampleBtn.innerHTML = originalHTML;
    }
});

// ============================================================================
// Clear Visualization
// ============================================================================

elements.clearBtn.addEventListener('click', () => {
    clearVisualization(true);
});

function clearVisualization(clearResponse = true) {
    // Reset nodes
    const nodes = document.querySelectorAll('.pipeline-node');
    nodes.forEach(node => {
        node.dataset.status = 'pending';
        node.style.cursor = 'default';
        node.onclick = null;
    });

    // Reset node details
    document.querySelectorAll('.node-details').forEach(details => {
        details.innerHTML = '<p class="detail-placeholder">Waiting...</p>';
    });
    document.getElementById('details-USER_INPUT').innerHTML = '<p class="detail-placeholder">Waiting for query...</p>';

    // Reset connector lines
    document.querySelectorAll('.connector-line').forEach(line => {
        line.classList.remove('active');
    });

    // Clear event log
    elements.eventLogContent.innerHTML = '<p class="log-empty">Events will appear here when you run a query...</p>';

    // Clear step data
    state.stepData = {};

    // Clear metrics and response
    if (clearResponse) {
        state.metrics = { embedding: null, search: null, llm: null, docs: null };
        document.getElementById('metric-total').textContent = '-';
        document.getElementById('metric-embedding').textContent = '-';
        document.getElementById('metric-search').textContent = '-';
        document.getElementById('metric-llm').textContent = '-';
        document.getElementById('metric-docs').textContent = '-';

        elements.responseContent.innerHTML = '<p class="response-placeholder">Response will appear here after pipeline completion...</p>';
        elements.stepDetailsContent.innerHTML = '<p class="details-placeholder">Select a completed step to view details...</p>';
    }

    // Reset request ID
    elements.requestId.textContent = '-';
    state.currentRequestId = null;
}

// ============================================================================
// Stats Update
// ============================================================================

function updateStats() {
    elements.totalQueries.textContent = state.stats.totalQueries;

    if (state.stats.durations.length > 0) {
        const avg = state.stats.durations.reduce((a, b) => a + b, 0) / state.stats.durations.length;
        elements.avgDuration.textContent = `${Math.round(avg)}ms`;
    } else {
        elements.avgDuration.textContent = '-';
    }

    if (state.stats.totalQueries > 0) {
        const rate = (state.stats.successCount / state.stats.totalQueries * 100).toFixed(0);
        elements.successRate.textContent = `${rate}%`;
    } else {
        elements.successRate.textContent = '-';
    }
}

// ============================================================================
// Notification
// ============================================================================

function showNotification(message, type = 'info') {
    // Create notification element
    const notification = document.createElement('div');
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 16px 24px;
        background: ${type === 'success' ? 'var(--color-success)' : type === 'error' ? 'var(--color-error)' : 'var(--color-accent)'};
        color: white;
        border-radius: var(--radius-md);
        box-shadow: var(--shadow-lg);
        z-index: 1000;
        animation: slideInRight 0.3s ease-out;
        max-width: 400px;
        font-size: 14px;
    `;
    notification.textContent = message;

    document.body.appendChild(notification);

    // Auto remove after 5 seconds
    setTimeout(() => {
        notification.style.animation = 'slideOutRight 0.3s ease-out forwards';
        setTimeout(() => notification.remove(), 300);
    }, 5000);
}

// Add notification animations
const notifStyle = document.createElement('style');
notifStyle.textContent = `
@keyframes slideInRight {
    from { transform: translateX(100%); opacity: 0; }
    to { transform: translateX(0); opacity: 1; }
}
@keyframes slideOutRight {
    from { transform: translateX(0); opacity: 1; }
    to { transform: translateX(100%); opacity: 0; }
}
`;
document.head.appendChild(notifStyle);

// ============================================================================
// Utility Functions
// ============================================================================

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============================================================================
// Initialization
// ============================================================================

window.addEventListener('DOMContentLoaded', () => {
    console.log('[App] RAG Pipeline Visualizer initialized');

    // Connect to WebSocket
    connectWebSocket();

    // Set sample question
    elements.questionInput.value = 'How many vacation days do employees receive per year?';

    // Focus on textarea
    elements.questionInput.focus();
});

// Handle page unload
window.addEventListener('beforeunload', () => {
    if (state.stompClient) {
        state.stompClient.disconnect();
    }
});
