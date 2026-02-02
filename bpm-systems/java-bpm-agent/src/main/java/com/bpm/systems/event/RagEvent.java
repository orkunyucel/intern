package com.bpm.systems.event;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * RAG Pipeline Event
 * <p>
 * Her RAG işlem adımı için event oluşturulur ve frontend'e gönderilir.
 */
public class RagEvent {

    private String requestId;
    private RagNode node;
    private NodeStatus status;
    private String message;
    private Long durationMs;
    private Map<String, Object> data;
    private LocalDateTime timestamp;
    private String error;

    public RagEvent() {
    }

    public RagEvent(String requestId, RagNode node, NodeStatus status, String message,
                   Long durationMs, Map<String, Object> data, LocalDateTime timestamp, String error) {
        this.requestId = requestId;
        this.node = node;
        this.status = status;
        this.message = message;
        this.durationMs = durationMs;
        this.data = data;
        this.timestamp = timestamp;
        this.error = error;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public RagNode getNode() {
        return node;
    }

    public void setNode(RagNode node) {
        this.node = node;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * RAG Pipeline Nodes
     */
    public enum RagNode {
        USER_INPUT,
        EMBEDDING_GENERATION,
        VECTOR_SEARCH,
        RETRIEVED_DOCUMENTS,
        LLM_CALL,
        RESPONSE_GENERATION,
        COMPLETED
    }

    /**
     * Node Status
     */
    public enum NodeStatus {
        PENDING,     // Beklemede (gri)
        PROCESSING,  // İşleniyor (mavi + spinning)
        COMPLETED,   // Tamamlandı (yeşil + tick)
        ERROR        // Hata (kırmızı + error icon)
    }

    public static class Builder {
        private String requestId;
        private RagNode node;
        private NodeStatus status;
        private String message;
        private Long durationMs;
        private Map<String, Object> data;
        private LocalDateTime timestamp;
        private String error;

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public Builder node(RagNode node) {
            this.node = node;
            return this;
        }

        public Builder status(NodeStatus status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public RagEvent build() {
            return new RagEvent(requestId, node, status, message, durationMs, data, timestamp, error);
        }
    }
}
