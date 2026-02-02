package com.mcp.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Agent Service Application
 *
 * LAYER: CLIENT (Agent)
 *
 * Main entry point for user requests.
 * Implements ReAct pattern with MCP tool discovery and LLM function calling.
 *
 * Port: 8081
 */
@SpringBootApplication
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
