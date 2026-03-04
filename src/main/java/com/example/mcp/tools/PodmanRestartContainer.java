package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Restarts a Podman container by name or ID.
 */
public class PodmanRestartContainer extends MCPTool {

    @Override
    public String name() { return "restart_container"; }

    @Override
    public String description() { return "Restart a Podman container by name or ID"; }

    @Override
    public ObjectNode inputSchema() {
        return schemaWithRequiredName("Container name or ID");
    }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        String name = input.path("name").asText();
        String result = exec("podman restart " + name);
        return MAPPER.getNodeFactory().textNode(result);
    }
}
