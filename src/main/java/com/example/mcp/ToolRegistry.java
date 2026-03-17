package com.example.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.mcp.tools.*;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages all registered MCP tools and dispatches JSON-RPC 2.0 requests.
 */
@ApplicationScoped
public class ToolRegistry {

    private static final String PROTOCOL_VERSION = "2024-11-05";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, MCPTool> tools = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        register(new PodmanListContainers());
        register(new PodmanStartContainer());
        register(new PodmanStopContainer());
        register(new PodmanRestartContainer());
        register(new PodmanLogs());
        register(new PodmanStatus());
        register(new Healthcheck());
        register(new PodmanRepairSocket());
    }

    private void register(MCPTool tool) {
        tools.put(tool.name(), tool);
    }

    /**
     * Handle a raw JSON-RPC line. Returns the response string, or null for notifications.
     */
    public String handle(String json) throws Exception {
        JsonNode request = mapper.readTree(json);
        String method = request.path("method").asText();
        JsonNode id = request.get("id");

        return switch (method) {
            case "initialize"               -> buildInitializeResponse(id);
            case "notifications/initialized" -> null; // notification — no response
            case "tools/list"               -> buildToolsListResponse(id);
            case "tools/call"               -> handleToolCall(request, id);
            case "ping"                     -> buildResult(id, mapper.createObjectNode());
            default                         -> buildError(id, -32601, "Method not found: " + method);
        };
    }

    // --- response builders ---

    private String buildInitializeResponse(JsonNode id) throws Exception {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", "podman-mcp");
        serverInfo.put("version", "1.0.0");
        return buildResult(id, result);
    }

    private String buildToolsListResponse(JsonNode id) throws Exception {
        ArrayNode toolList = mapper.createArrayNode();
        for (MCPTool tool : tools.values()) {
            ObjectNode t = mapper.createObjectNode();
            t.put("name", tool.name());
            t.put("description", tool.description());
            t.set("inputSchema", tool.inputSchema());
            toolList.add(t);
        }
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", toolList);
        return buildResult(id, result);
    }

    private String handleToolCall(JsonNode request, JsonNode id) throws Exception {
        JsonNode params = request.path("params");
        String toolName = params.path("name").asText();
        JsonNode arguments = params.path("arguments");

        MCPTool tool = tools.get(toolName);
        if (tool == null) {
            return buildError(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            JsonNode output = tool.run(arguments);
            String raw = output.isTextual() ? output.asText() : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            // Wrap in a code fence so the CLI renderer treats colons as literal characters
            String text = "```json\n" + raw + "\n```";
            ObjectNode result = mapper.createObjectNode();
            ArrayNode content = result.putArray("content");
            ObjectNode item = content.addObject();
            item.put("type", "text");
            item.put("text", text);
            return buildResult(id, result);
        } catch (Exception e) {
            return buildError(id, -32603, "Tool error: " + e.getMessage());
        }
    }

    private String buildResult(JsonNode id, JsonNode result) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) response.set("id", id);
        response.set("result", result);
        return mapper.writeValueAsString(response);
    }

    private String buildError(JsonNode id, int code, String message) throws Exception {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id != null) response.set("id", id);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return mapper.writeValueAsString(response);
    }
}
