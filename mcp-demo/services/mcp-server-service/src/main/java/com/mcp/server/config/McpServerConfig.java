package com.mcp.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * MCP Server Configuration
 */
@Configuration
public class McpServerConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
