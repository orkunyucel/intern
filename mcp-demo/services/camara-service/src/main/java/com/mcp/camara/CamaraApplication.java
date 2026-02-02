package com.mcp.camara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CAMARA Service Application
 *
 * LAYER: CAMARA API
 *
 * Bridges MCP Server to CAMARA QoD API.
 * Supports both REAL mode (actual CAMARA API) and MOCK mode (via network-mock-service).
 *
 * Port: 8084
 */
@SpringBootApplication
public class CamaraApplication {

    public static void main(String[] args) {
        SpringApplication.run(CamaraApplication.class, args);
    }
}
