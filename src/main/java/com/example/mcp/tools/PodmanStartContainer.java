package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Starts a Podman container by name or ID.
 */
public class PodmanStartContainer extends MCPTool {

    @Override
    public String name() { return "start_container"; }

    @Override
    public String description() { return "Start a Podman container by name or ID"; }

    @Override
    public ObjectNode inputSchema() {
        return schemaWithRequiredName("Container name or ID");
    }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        String name = input.path("name").asText();
        String result = exec("podman start " + name);
        return MAPPER.getNodeFactory().textNode(result);
    }
}
