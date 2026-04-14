package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Performs a full health check of the local development environment:
 * Podman socket, running containers, and optional service port checks.
 */
public class Healthcheck extends MCPTool {

    @Override
    public String name() { return "healthcheck"; }

    @Override
    public String description() { return "Full health check: Podman socket, running containers, and key service ports"; }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        ObjectNode result = MAPPER.createObjectNode();

        // 1. Podman socket
        String podmanCheck = exec("podman info >/dev/null 2>&1 && echo ok || echo fail");
        result.put("podman", podmanCheck.contains("ok") ? "running" : "unavailable");

        // 2. Running containers
        String runningJson = exec("podman ps --format json");
        String cleanedJson = stripNonJsonPrefix(runningJson);
        ObjectNode services = result.putObject("services");
        try {
            JsonNode containers = MAPPER.readTree(cleanedJson.isBlank() ? "[]" : cleanedJson);
            if (containers.isArray()) {
                for (JsonNode c : containers) {
                    String cname = c.path("Names").isArray()
                            ? c.path("Names").get(0).asText()
                            : c.path("Names").asText("unknown");
                    String state = c.path("State").asText("unknown");
                    services.put(cname, state);
                }
            }
        } catch (Exception e) {
            services.put("error", "Could not parse container list: " + e.getMessage());
        }

        // 3. Optional port checks (SonarQube on 9000, common dev services)
        ObjectNode ports = result.putObject("ports");
        ports.put("9000", checkPort(9000));  // SonarQube
        ports.put("5432", checkPort(5432));  // PostgreSQL
        ports.put("6379", checkPort(6379));  // Redis

        return result;
    }

    private String checkPort(int port) {
        try {
            String out = exec("bash -c 'echo > /dev/tcp/localhost/" + port + "' 2>&1 && echo open || echo closed");
            return out.contains("open") ? "open" : "closed";
        } catch (Exception e) {
            return "closed";
        }
    }
}
