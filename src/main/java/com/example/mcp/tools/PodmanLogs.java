package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Retrieves logs from a Podman container, with optional line limit.
 */
public class PodmanLogs extends MCPTool {

    @Override
    public String name() { return "logs_container"; }

    @Override
    public String description() { return "Get logs from a Podman container (optional: limit number of lines)"; }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        ObjectNode nameProp = props.putObject("name");
        nameProp.put("type", "string");
        nameProp.put("description", "Container name or ID");

        ObjectNode linesProp = props.putObject("lines");
        linesProp.put("type", "integer");
        linesProp.put("description", "Number of tail lines to retrieve (default: 100)");

        schema.putArray("required").add("name");
        return schema;
    }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        String name = input.path("name").asText();
        int lines = input.path("lines").asInt(100);
        String result = exec("podman logs --tail=" + lines + " " + name);
        return MAPPER.getNodeFactory().textNode(result);
    }
}
