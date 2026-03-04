package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lists all Podman containers (running and stopped).
 */
public class PodmanListContainers extends MCPTool {

    @Override
    public String name() { return "list_containers"; }

    @Override
    public String description() { return "List all Podman containers (running and stopped)"; }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        String result = exec("podman ps -a --format json");
        return MAPPER.readTree(result.isBlank() ? "[]" : result);
    }
}
