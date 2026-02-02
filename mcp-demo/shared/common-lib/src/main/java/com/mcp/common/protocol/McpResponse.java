package com.mcp.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MCP JSON-RPC 2.0 Response
 *
 * Standard JSON-RPC response format used in MCP protocol.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResponse {

    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    @JsonProperty("id")
    private Object id;

    @JsonProperty("result")
    private Object result;

    @JsonProperty("error")
    private McpError error;

    public McpResponse() {
    }

    public McpResponse(Object id, Object result) {
        this.id = id;
        this.result = result;
    }

    public McpResponse(Object id, McpError error) {
        this.id = id;
        this.error = error;
    }

    // Static factory methods
    public static McpResponse success(Object id, Object result) {
        return new McpResponse(id, result);
    }

    public static McpResponse error(Object id, int code, String message) {
        return new McpResponse(id, new McpError(code, message));
    }

    public static McpResponse methodNotFound(Object id) {
        return error(id, -32601, "Method not found");
    }

    public static McpResponse invalidRequest(Object id) {
        return error(id, -32600, "Invalid Request");
    }

    public static McpResponse parseError() {
        return error(null, -32700, "Parse error");
    }

    public static McpResponse internalError(Object id, String message) {
        return error(id, -32603, "Internal error: " + message);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public boolean isError() {
        return error != null;
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

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public McpError getError() {
        return error;
    }

    public void setError(McpError error) {
        this.error = error;
    }
}
