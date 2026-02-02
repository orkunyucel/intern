package com.mcp.agent.service;

import com.mcp.common.protocol.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * MCP Client
 *
 * Communicates with MCP Server via JSON-RPC 2.0 protocol.
 * Provides methods for tool discovery and tool calling.
 */
@Component
public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mcp-server.url:http://localhost:8083/mcp/jsonrpc}")
    private String mcpServerUrl;

    private int requestId = 0;
    private List<Tool> cachedTools;

    public McpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Initialize connection with MCP Server
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> initialize() {
        log.info("Initializing MCP connection to: {}", mcpServerUrl);

        InitializeParams params = new InitializeParams();
        params.setClientInfo(new InitializeParams.ClientInfo("camara-ai-agent", "1.0.0"));

        McpRequest request = McpRequest.initialize(nextId(), params);
        McpResponse response = sendRequest(request);

        if (response.isError()) {
            throw new RuntimeException("Initialize failed: " + response.getError().getMessage());
        }

        log.info("MCP initialized successfully");
        return (Map<String, Object>) response.getResult();
    }

    /**
     * Get list of available tools from MCP Server
     */
    @SuppressWarnings("unchecked")
    public List<Tool> listTools() {
        log.info("Requesting tools list from MCP Server");

        McpRequest request = McpRequest.toolsList(nextId());
        McpResponse response = sendRequest(request);

        if (response.isError()) {
            throw new RuntimeException("tools/list failed: " + response.getError().getMessage());
        }

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        List<Map<String, Object>> toolMaps = (List<Map<String, Object>>) result.get("tools");

        List<Tool> tools = toolMaps.stream()
                .map(this::mapToTool)
                .toList();

        this.cachedTools = tools;
        log.info("Received {} tools from MCP Server", tools.size());

        return tools;
    }

    /**
     * Call a tool on MCP Server
     */
    @SuppressWarnings("unchecked")
    public ToolResult callTool(String toolName, Map<String, Object> arguments) {
        log.info("Calling tool: {} with arguments: {}", toolName, arguments);

        ToolCallParams params = new ToolCallParams(toolName, arguments);
        McpRequest request = McpRequest.toolsCall(nextId(), params);

        McpResponse response = sendRequest(request);

        if (response.isError()) {
            log.error("Tool call failed: {}", response.getError().getMessage());
            return ToolResult.error(response.getError().getMessage());
        }

        Map<String, Object> result = (Map<String, Object>) response.getResult();
        return mapToToolResult(result);
    }

    public List<Tool> getCachedTools() {
        if (cachedTools == null) {
            return listTools();
        }
        return cachedTools;
    }

    public void clearCache() {
        this.cachedTools = null;
    }

    private McpResponse sendRequest(McpRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<McpRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<McpResponse> response = restTemplate.exchange(
                    mcpServerUrl,
                    HttpMethod.POST,
                    entity,
                    McpResponse.class);

            return response.getBody();

        } catch (Exception e) {
            log.error("MCP request failed: {}", e.getMessage());
            throw new RuntimeException("MCP request failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Tool mapToTool(Map<String, Object> map) {
        Tool tool = new Tool();
        tool.setName((String) map.get("name"));
        tool.setDescription((String) map.get("description"));
        tool.setInputSchema((Map<String, Object>) map.get("inputSchema"));
        return tool;
    }

    @SuppressWarnings("unchecked")
    private ToolResult mapToToolResult(Map<String, Object> map) {
        List<Map<String, Object>> contentList = (List<Map<String, Object>>) map.get("content");
        Boolean isError = (Boolean) map.get("isError");

        List<ToolResult.Content> content = contentList.stream()
                .map(c -> new ToolResult.Content(
                        (String) c.get("type"),
                        (String) c.get("text")))
                .toList();

        return new ToolResult(content, isError != null && isError);
    }

    private synchronized int nextId() {
        return ++requestId;
    }
}
