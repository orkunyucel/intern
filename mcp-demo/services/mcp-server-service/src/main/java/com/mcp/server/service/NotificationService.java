package com.mcp.server.service;

import com.mcp.common.model.AsyncTask;
import com.mcp.common.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Notification Service
 *
 * Handles incoming webhook notifications from CAMARA/Network services
 * and forwards them to registered SSE listeners (Agent connections).
 *
 * Flow:
 * 1. Agent registers SSE emitter for a taskId
 * 2. CAMARA/Network sends notification via POST /notify
 * 3. This service routes notification to correct emitter
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final TaskTracker taskTracker;

    // SSE emitters waiting for task updates
    private final ConcurrentHashMap<String, SseEmitter> taskEmitters = new ConcurrentHashMap<>();

    public NotificationService(TaskTracker taskTracker) {
        this.taskTracker = taskTracker;
    }

    /**
     * Register SSE emitter for a task
     */
    public void registerEmitter(String taskId, SseEmitter emitter) {
        taskEmitters.put(taskId, emitter);
        log.info("SSE emitter registered for task: {}", taskId);

        // Cleanup on completion
        emitter.onCompletion(() -> {
            taskEmitters.remove(taskId);
            log.debug("SSE emitter removed for task: {}", taskId);
        });
        emitter.onTimeout(() -> {
            taskEmitters.remove(taskId);
            log.debug("SSE emitter timed out for task: {}", taskId);
        });
    }

    /**
     * Process incoming notification from CAMARA/Network
     */
    public void processNotification(NotificationEvent event) {
        log.info("Processing notification: {}", event);

        String taskId = event.getTaskId();
        if (taskId == null) {
            log.warn("Notification without taskId, ignoring: {}", event);
            return;
        }

        AsyncTask task = taskTracker.getTask(taskId);
        if (task == null) {
            log.warn("Unknown taskId: {}", taskId);
            return;
        }

        // Update task status based on event type
        if (event.getType() != null) {
            if (event.getType().contains("available") ||
                    event.getType().contains("bandwidth-changed")) {
                taskTracker.markAvailable(taskId, event.getData());
            } else if (event.getType().contains("unavailable") ||
                    event.getType().contains("failed")) {
                String reason = event.getData() != null ? String.valueOf(event.getData().get("reason"))
                        : "Unknown error";
                taskTracker.markFailed(taskId, reason);
            } else if (event.getType().contains("working") ||
                    event.getType().contains("progress")) {
                taskTracker.markWorking(taskId);
            }
        }

        // Forward to registered SSE emitter
        forwardToEmitter(taskId, event);
    }

    /**
     * Forward notification to SSE emitter
     */
    private void forwardToEmitter(String taskId, NotificationEvent event) {
        SseEmitter emitter = taskEmitters.get(taskId);
        if (emitter == null) {
            log.debug("No SSE emitter registered for task: {}", taskId);
            return;
        }

        try {
            Map<String, Object> sseData = Map.of(
                    "taskId", taskId,
                    "type", event.getType() != null ? event.getType() : "unknown",
                    "event", taskTracker.getTask(taskId).getEvent(),
                    "status", taskTracker.getTask(taskId).getStatus().name(),
                    "data", event.getData() != null ? event.getData() : Map.of());

            emitter.send(SseEmitter.event()
                    .name("task-update")
                    .data(sseData));

            log.info("Notification forwarded to SSE for task: {}", taskId);

            // Complete emitter if task is done
            AsyncTask task = taskTracker.getTask(taskId);
            if (task.getStatus() == AsyncTask.Status.AVAILABLE ||
                    task.getStatus() == AsyncTask.Status.FAILED) {
                emitter.complete();
                taskEmitters.remove(taskId);
            }

        } catch (IOException e) {
            log.error("Failed to send SSE event for task {}: {}", taskId, e.getMessage());
            emitter.completeWithError(e);
            taskEmitters.remove(taskId);
        }
    }

    /**
     * Get registered emitter count (for health check)
     */
    public int getEmitterCount() {
        return taskEmitters.size();
    }
}
