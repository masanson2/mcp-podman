package com.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Base class for all MCP tools. Each subclass represents one callable tool.
 */
public abstract class MCPTool {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    /** MCP tool name (used in tools/list and tools/call). */
    public abstract String name();

    /** Human-readable description shown to the LLM. */
    public abstract String description();

    /** JSON Schema for the tool's input arguments. */
    public ObjectNode inputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        return schema;
    }

    /** Execute the tool logic given parsed arguments. Return a JsonNode result. */
    public abstract JsonNode run(JsonNode input) throws Exception;

    /** Helper: run a bash command and return its combined stdout+stderr output. */
    protected String exec(String cmd) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", "-c", cmd)
                .redirectErrorStream(true)
                .start();
        process.waitFor();
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    /** Helper: build a schema with a single required string property. */
    protected static ObjectNode schemaWithRequiredName(String description) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        ObjectNode nameProp = props.putObject("name");
        nameProp.put("type", "string");
        nameProp.put("description", description);
        schema.putArray("required").add("name");
        return schema;
    }
}
