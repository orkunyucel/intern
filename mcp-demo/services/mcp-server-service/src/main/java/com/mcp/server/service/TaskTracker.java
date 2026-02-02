package com.mcp.server.service;

import com.mcp.common.model.AsyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Task Tracker Service
 *
 * Tracks async tasks (QoS session creation, bandwidth changes, etc.)
 * and routes notifications to registered listeners.
 *
 * Thread-safe with TTL-based cleanup.
 */
@Service
public class TaskTracker {

    private static final Logger log = LoggerFactory.getLogger(TaskTracker.class);
    private static final long TASK_TTL_SECONDS = 300; // 5 minutes

    private final ConcurrentHashMap<String, AsyncTask> tasks = new ConcurrentHashMap<>();

    /**
     * Register a new async task
     */
    public AsyncTask createTask(String toolName, Map<String, Object> arguments, String notificationUrl) {
        AsyncTask task = new AsyncTask(toolName, arguments, notificationUrl);
        tasks.put(task.getTaskId(), task);
        log.info("Task created: {}", task);
        cleanupExpiredTasks();
        return task;
    }

    /**
     * Get task by ID
     */
    public AsyncTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    /**
     * Update task status to WORKING
     */
    public void markWorking(String taskId) {
        AsyncTask task = tasks.get(taskId);
        if (task != null) {
            task.markWorking();
            log.info("Task {} marked as WORKING", taskId);
        }
    }

    /**
     * Update task status to AVAILABLE with result
     */
    public void markAvailable(String taskId, Map<String, Object> result) {
        AsyncTask task = tasks.get(taskId);
        if (task != null) {
            task.markAvailable(result);
            log.info("Task {} marked as AVAILABLE with result: {}", taskId, result);
        }
    }

    /**
     * Update task status to FAILED
     */
    public void markFailed(String taskId, String errorMessage) {
        AsyncTask task = tasks.get(taskId);
        if (task != null) {
            task.markFailed(errorMessage);
            log.error("Task {} marked as FAILED: {}", taskId, errorMessage);
        }
    }

    /**
     * Get all active tasks
     */
    public List<AsyncTask> getActiveTasks() {
        return tasks.values().stream()
                .filter(t -> t.getStatus() == AsyncTask.Status.REQUESTED ||
                        t.getStatus() == AsyncTask.Status.WORKING)
                .collect(Collectors.toList());
    }

    /**
     * Remove completed/expired tasks
     */
    private void cleanupExpiredTasks() {
        Instant cutoff = Instant.now().minusSeconds(TASK_TTL_SECONDS);
        tasks.entrySet().removeIf(entry -> {
            AsyncTask task = entry.getValue();
            if (task.getCreatedAt().isBefore(cutoff)) {
                log.debug("Removing expired task: {}", task.getTaskId());
                return true;
            }
            return false;
        });
    }

    /**
     * Get task count (for health check)
     */
    public int getTaskCount() {
        return tasks.size();
    }

    /**
     * Get active task count
     */
    public int getActiveTaskCount() {
        return (int) tasks.values().stream()
                .filter(t -> t.getStatus() == AsyncTask.Status.REQUESTED ||
                        t.getStatus() == AsyncTask.Status.WORKING)
                .count();
    }
}
