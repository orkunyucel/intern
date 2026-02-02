package com.mcp.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of tools/list method
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolsListResult {

    @JsonProperty("tools")
    private List<Tool> tools;

    public ToolsListResult() {
    }

    public ToolsListResult(List<Tool> tools) {
        this.tools = tools;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }
}
