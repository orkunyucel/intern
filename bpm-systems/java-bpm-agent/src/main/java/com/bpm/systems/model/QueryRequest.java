package com.bpm.systems.model;

/**
 * RAG Query Request DTO
 */
public class QueryRequest {

    private String question;
    private String filterExpression;

    public QueryRequest() {
    }

    public QueryRequest(String question, String filterExpression) {
        this.question = question;
        this.filterExpression = filterExpression;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getFilterExpression() {
        return filterExpression;
    }

    public void setFilterExpression(String filterExpression) {
        this.filterExpression = filterExpression;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String question;
        private String filterExpression;

        public Builder question(String question) {
            this.question = question;
            return this;
        }

        public Builder filterExpression(String filterExpression) {
            this.filterExpression = filterExpression;
            return this;
        }

        public QueryRequest build() {
            return new QueryRequest(question, filterExpression);
        }
    }
}
