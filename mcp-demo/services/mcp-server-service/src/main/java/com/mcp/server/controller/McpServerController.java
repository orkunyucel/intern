package com.mcp.server.controller;

import com.mcp.common.model.AsyncTask;
import com.mcp.common.model.NotificationEvent;
import com.mcp.common.protocol.*;
import com.mcp.server.service.NotificationService;
import com.mcp.server.service.TaskTracker;
import com.mcp.server.service.ToolExecutor;
import com.mcp.server.service.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * MCP Server Controller
 *
 * JSON-RPC 2.0 endpoint for MCP protocol.
 * Handles: initialize, tools/list, tools/call
 * Also provides webhook endpoint for async notifications.
 */
@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "*")
public class McpServerController {

    private static final Logger log = LoggerFactory.getLogger(McpServerController.class);

    private final ToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final TaskTracker taskTracker;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    private static final String SERVER_NAME = "camara-mcp-server";
    private static final String SERVER_VERSION = "1.0.0";
    private static final String PROTOCOL_VERSION = "2025-11-05";

    public McpServerController(ToolRegistry toolRegistry, ToolExecutor toolExecutor,
            TaskTracker taskTracker, NotificationService notificationService) {
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.taskTracker = taskTracker;
        this.notificationService = notificationService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Main JSON-RPC endpoint
     */
    @PostMapping(value = "/jsonrpc", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public McpResponse handleRequest(@RequestBody McpRequest request) {
        log.info("MCP Request: method={}, id={}", request.getMethod(), request.getId());

        try {
            return switch (request.getMethod()) {
                case "initialize" -> handleInitialize(request);
                case "tools/list" -> handleToolsList(request);
                case "tools/call" -> handleToolsCall(request);
                default -> McpResponse.methodNotFound(request.getId());
            };
        } catch (Exception e) {
            log.error("Error handling MCP request: {}", e.getMessage(), e);
            return McpResponse.internalError(request.getId(), e.getMessage());
        }
    }

    private McpResponse handleInitialize(McpRequest request) {
        log.info("Initialize request received");

        Map<String, Object> result = Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(
                        "tools", Map.of(),
                        "notifications", Map.of("enabled", true)),
                "serverInfo", Map.of(
                        "name", SERVER_NAME,
                        "version", SERVER_VERSION));

        return McpResponse.success(request.getId(), result);
    }

    private McpResponse handleToolsList(McpRequest request) {
        log.info("tools/list request - returning {} tools", toolRegistry.getToolCount());

        ToolsListResult result = new ToolsListResult(toolRegistry.getAllTools());
        return McpResponse.success(request.getId(), result);
    }

    @SuppressWarnings("unchecked")
    private McpResponse handleToolsCall(McpRequest request) {
        ToolCallParams params;
        try {
            if (request.getParams() instanceof ToolCallParams) {
                params = (ToolCallParams) request.getParams();
            } else if (request.getParams() instanceof Map) {
                Map<String, Object> paramsMap = (Map<String, Object>) request.getParams();
                params = new ToolCallParams(
                        (String) paramsMap.get("name"),
                        (Map<String, Object>) paramsMap.get("arguments"));
            } else {
                return McpResponse.error(request.getId(),
                        McpError.INVALID_PARAMS, "Invalid params format");
            }
        } catch (Exception e) {
            return McpResponse.error(request.getId(),
                    McpError.INVALID_PARAMS, "Failed to parse params: " + e.getMessage());
        }

        if (params.getName() == null || params.getName().isBlank()) {
            return McpResponse.error(request.getId(),
                    McpError.INVALID_PARAMS, "Tool name is required");
        }

        log.info("tools/call: {} with args: {}", params.getName(), params.getArguments());

        // Check if this is an async tool (session creation, bandwidth change)
        if (isAsyncTool(params.getName())) {
            return handleAsyncToolCall(request, params);
        }

        ToolResult result = toolExecutor.execute(params.getName(), params.getArguments());
        return McpResponse.success(request.getId(), result);
    }

    /**
     * Check if tool should be executed asynchronously
     */
    private boolean isAsyncTool(String toolName) {
        return toolName.equals("create_qos_session") ||
                toolName.equals("set_bandwidth");
    }

    /**
     * Handle async tool call - returns taskId, actual result comes via notification
     */
    private McpResponse handleAsyncToolCall(McpRequest request, ToolCallParams params) {
        // Create async task
        String notificationUrl = "http://localhost:8083/mcp/notify"; // Self URL for notifications
        AsyncTask task = taskTracker.createTask(params.getName(), params.getArguments(), notificationUrl);

        log.info("Async tool call started: {} -> taskId: {}", params.getName(), task.getTaskId());

        // Execute tool with notificationUrl
        Map<String, Object> argsWithNotification = new java.util.HashMap<>(
                params.getArguments() != null ? params.getArguments() : Map.of());
        argsWithNotification.put("notificationUrl", notificationUrl);
        argsWithNotification.put("taskId", task.getTaskId());

        // Start async execution
        toolExecutor.executeAsync(params.getName(), argsWithNotification);

        // Return immediate response with taskId - use text format
        String responseText = String.format(
                "Task accepted for async execution.\n\nTask ID: %s\nStatus: %s\nMessage: Waiting for completion notification",
                task.getTaskId(),
                task.getStatus().name());
        ToolResult asyncResult = ToolResult.text(responseText);

        return McpResponse.success(request.getId(), asyncResult);
    }

    /**
     * Webhook endpoint for receiving notifications from CAMARA/Network services
     */
    @PostMapping(value = "/notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleNotification(@RequestBody NotificationEvent event) {
        log.info("Received notification: type={}, taskId={}", event.getType(), event.getTaskId());

        try {
            notificationService.processNotification(event);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Notification processed"));
        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()));
        }
    }

    /**
     * SSE endpoint for subscribing to task updates
     */
    @GetMapping(value = "/tasks/{taskId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToTask(@PathVariable String taskId) {
        SseEmitter emitter = new SseEmitter(120000L); // 2 min timeout

        AsyncTask task = taskTracker.getTask(taskId);
        if (task == null) {
            emitter.completeWithError(new IllegalArgumentException("Unknown taskId: " + taskId));
            return emitter;
        }

        notificationService.registerEmitter(taskId, emitter);
        log.info("SSE subscription registered for task: {}", taskId);

        return emitter;
    }

    /**
     * Get task status
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        AsyncTask task = taskTracker.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "taskId", task.getTaskId(),
                "status", task.getStatus().name(),
                "event", task.getEvent() != null ? task.getEvent() : "",
                "toolName", task.getToolName(),
                "createdAt", task.getCreatedAt().toString(),
                "updatedAt", task.getUpdatedAt().toString(),
                "result", task.getResult() != null ? task.getResult() : Map.of()));
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "mcp-server-service",
                "port", 8082,
                "server", SERVER_NAME,
                "version", SERVER_VERSION,
                "protocol", PROTOCOL_VERSION,
                "toolCount", toolRegistry.getToolCount(),
                "activeTasks", taskTracker.getActiveTaskCount(),
                "sseEmitters", notificationService.getEmitterCount());
    }

    /**
     * Simple tools list endpoint (non JSON-RPC, for testing)
     */
    @GetMapping("/tools")
    public ToolsListResult listToolsSimple() {
        return new ToolsListResult(toolRegistry.getAllTools());
    }
}
