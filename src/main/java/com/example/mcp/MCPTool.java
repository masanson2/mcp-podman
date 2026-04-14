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
        String output = execRaw(cmd);
        if (isRunUserDirError(output)) {
            String repairResult = repairRunUserDir();
            System.err.println("[MCPTool] Podman socket repair attempted. Result: " + repairResult);
            output = execRaw(cmd);
        }
        return output;
    }

    /** Runs a bash command without any auto-repair logic. */
    protected String execRaw(String cmd) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", "-c", cmd)
                .redirectErrorStream(true)
                .start();
        process.waitFor();
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    /**
     * Returns true when the command output indicates the rootless Podman runtime
     * directory (/run/user/<uid>) is missing.
     */
    protected static boolean isRunUserDirError(String output) {
        String lower = output.toLowerCase();
        return lower.contains("/run/user/")
                && (lower.contains("no such file") || lower.contains("not found") || lower.contains("missing"));
    }

    /**
     * Recreates /run/user/$(id -u) with the correct ownership and permissions,
     * then (re)starts the rootless Podman socket via systemd.
     * Shell-level command substitution is used so no user-supplied data is
     * interpolated into the command string from Java.
     *
     * @return the combined output of all repair commands
     */
    protected String repairRunUserDir() throws IOException, InterruptedException {
        String repair = "sudo mkdir -p /run/user/$(id -u)"
                + " && sudo chown $(id -un):$(id -un) /run/user/$(id -u)"
                + " && sudo chmod 700 /run/user/$(id -u)"
                + " && systemctl --user start podman.socket 2>&1"
                + " && echo REPAIR_OK";
        return execRaw(repair);
    }

    /**
     * Strips any non-JSON prefix from Podman output.
     *
     * <p>Podman (especially in rootless mode) occasionally emits text warnings on
     * stderr before the actual JSON payload (e.g. {@code time="..." level=warning
     * msg="no systemd user session available"}). Because {@link #execRaw} merges
     * stderr into stdout via {@code redirectErrorStream(true)}, those lines appear
     * at the start of the combined output and break JSON parsing.
     *
     * <p>This method locates the first {@code [} or {@code {} character and returns
     * the substring from that point onward, effectively discarding any leading
     * warning lines. If no JSON start character is found the original string is
     * returned unchanged so callers can handle the "empty / non-JSON" case as
     * before.
     *
     * @param output raw command output, possibly with leading warning lines
     * @return the output with any non-JSON prefix removed
     */
    protected static String stripNonJsonPrefix(String output) {
        if (output == null || output.isBlank()) {
            return output;
        }
        int arrayStart  = output.indexOf('[');
        int objectStart = output.indexOf('{');
        int jsonStart;
        if (arrayStart < 0 && objectStart < 0) {
            return output; // no JSON found – return as-is
        } else if (arrayStart < 0) {
            jsonStart = objectStart;
        } else if (objectStart < 0) {
            jsonStart = arrayStart;
        } else {
            jsonStart = Math.min(arrayStart, objectStart);
        }
        return output.substring(jsonStart);
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
