package com.mcp.llm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * LLM Service Application
 *
 * LAYER: LLM
 *
 * Wrapper service for Gemini API.
 * Provides function calling support for AI Agent.
 *
 * Port: 8082
 */
@SpringBootApplication
public class LlmApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmApplication.class, args);
    }
}
