package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Checks whether the Podman rootless socket is active and usable.
 */
public class PodmanStatus extends MCPTool {

    @Override
    public String name() { return "podman_status"; }

    @Override
    public String description() { return "Check the status of the Podman rootless socket and daemon"; }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        ObjectNode result = MAPPER.createObjectNode();

        // Check if podman socket is accessible
        String socketCheck = exec("podman info --format '{{.Host.RemoteSocket.Path}}' 2>&1");
        boolean socketExists = !socketCheck.contains("error") && !socketCheck.isBlank();
        result.put("socket", socketExists ? "active" : "unavailable");
        result.put("socketPath", socketCheck);

        // Quick podman version check
        String version = exec("podman version --format '{{.Client.Version}}' 2>&1");
        result.put("version", version.isBlank() ? "unknown" : version);

        // Rootless user check
        String user = exec("id -un");
        result.put("user", user);

        return result;
    }
}
