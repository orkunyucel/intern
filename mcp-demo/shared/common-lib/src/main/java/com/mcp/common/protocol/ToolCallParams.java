package com.mcp.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Parameters for tools/call method
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallParams {

    @JsonProperty("name")
    private String name;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    public ToolCallParams() {
    }

    public ToolCallParams(String name, Map<String, Object> arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments;
    }
}
