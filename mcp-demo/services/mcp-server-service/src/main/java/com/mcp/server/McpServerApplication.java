package com.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Server Service Application
 *
 * LAYER: MCP SERVER
 *
 * JSON-RPC 2.0 server for MCP protocol.
 * Handles: initialize, tools/list, tools/call
 *
 * Port: 8083
 */
@SpringBootApplication
public class McpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}
