package com.example.mcp;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Main entry point. Reads JSON-RPC 2.0 messages from stdin (one per line)
 * and writes responses to stdout, following the MCP stdio transport protocol.
 */
@QuarkusMain
public class MCPServer implements QuarkusApplication {

    @Inject
    ToolRegistry toolRegistry;

    @Override
    public int run(String... args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                String response = toolRegistry.handle(line);
                if (response != null) {
                    System.out.println(response);
                    System.out.flush();
                }
            } catch (Exception e) {
                System.err.println("[MCPServer] Error handling request: " + e.getMessage());
            }
        }
        return 0;
    }
}
