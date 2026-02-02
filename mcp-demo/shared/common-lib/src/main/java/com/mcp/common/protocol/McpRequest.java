package com.mcp.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MCP JSON-RPC 2.0 Request
 *
 * Standard JSON-RPC request format used in MCP protocol.
 * Supports methods: initialize, tools/list, tools/call
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpRequest {

    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    @JsonProperty("id")
    private Object id;

    @JsonProperty("method")
    private String method;

    @JsonProperty("params")
    private Object params;

    public McpRequest() {
    }

    public McpRequest(Object id, String method, Object params) {
        this.id = id;
        this.method = method;
        this.params = params;
    }

    // Static factory methods
    public static McpRequest toolsList(Object id) {
        return new McpRequest(id, "tools/list", null);
    }

    public static McpRequest toolsCall(Object id, ToolCallParams params) {
        return new McpRequest(id, "tools/call", params);
    }

    public static McpRequest initialize(Object id, InitializeParams params) {
        return new McpRequest(id, "initialize", params);
    }

    // Getters and Setters
    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }
}
