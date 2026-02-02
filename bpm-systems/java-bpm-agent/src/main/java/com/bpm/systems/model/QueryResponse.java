package com.bpm.systems.model;

import java.time.LocalDateTime;

/**
 * RAG Query Response DTO
 */
public class QueryResponse {

    private String question;
    private String answer;
    private LocalDateTime timestamp;
    private long durationMs;
    private boolean success;
    private String error;

    public QueryResponse() {
    }

    public QueryResponse(String question, String answer, LocalDateTime timestamp,
                        long durationMs, boolean success, String error) {
        this.question = question;
        this.answer = answer;
        this.timestamp = timestamp;
        this.durationMs = durationMs;
        this.success = success;
        this.error = error;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
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

    public static class Builder {
        private String question;
        private String answer;
        private LocalDateTime timestamp;
        private long durationMs;
        private boolean success;
        private String error;

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder answer(String answer) {
            this.answer = answer;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public QueryResponse build() {
            return new QueryResponse(question, answer, timestamp, durationMs, success, error);
        }
    }
}
