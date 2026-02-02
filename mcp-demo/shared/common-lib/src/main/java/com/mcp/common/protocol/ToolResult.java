package com.mcp.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * MCP Tool Result
 *
 * Result of a tool execution. Contains content array with text or other types.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {

    @JsonProperty("content")
    private List<Content> content;

    @JsonProperty("isError")
    private Boolean isError;

    public ToolResult() {
    }

    public ToolResult(List<Content> content) {
        this.content = content;
    }

    public ToolResult(List<Content> content, boolean isError) {
        this.content = content;
        this.isError = isError;
    }

    // Factory methods
    public static ToolResult text(String text) {
        return new ToolResult(List.of(Content.text(text)));
    }

    public static ToolResult error(String errorMessage) {
        return new ToolResult(List.of(Content.text(errorMessage)), true);
    }

    // Getters and Setters
    public List<Content> getContent() {
        return content;
    }

    public void setContent(List<Content> content) {
        this.content = content;
    }

    public Boolean getIsError() {
        return isError;
    }

    public void setIsError(Boolean isError) {
        this.isError = isError;
    }

    /**
     * Content item in tool result
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Content {

        @JsonProperty("type")
        private String type;

        @JsonProperty("text")
        private String text;

        public Content() {
        }

        public Content(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public static Content text(String text) {
            return new Content("text", text);
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
