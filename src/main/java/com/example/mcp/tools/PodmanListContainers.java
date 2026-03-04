package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Lists all Podman containers (running and stopped).
 * Accepts optional boolean argument "running_only" to filter only running containers.
 */
public class PodmanListContainers extends MCPTool {

    @Override
    public String name() { return "list_containers"; }

    @Override
    public String description() { return "List all Podman containers (running and stopped). Pass running_only=true to get only running containers."; }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode runningOnly = props.putObject("running_only");
        runningOnly.put("type", "boolean");
        runningOnly.put("description", "If true, returns only running containers (default: false)");
        return schema;
    }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        boolean runningOnly = input.path("running_only").asBoolean(false);
        String cmd = runningOnly ? "podman ps --format json" : "podman ps -a --format json";
        String result = exec(cmd);
        return MAPPER.readTree(result.isBlank() ? "[]" : result);
    }
}
