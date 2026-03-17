package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Repairs the rootless Podman socket when /run/user/&lt;uid&gt; is missing.
 *
 * <p>Recreates the runtime directory with the correct ownership and permissions,
 * then (re)starts the Podman socket via systemd. This is the manual equivalent
 * of the auto-repair that {@link MCPTool#exec(String)} performs transparently
 * whenever any Podman command fails with a "No such file" error on that path.
 */
public class PodmanRepairSocket extends MCPTool {

    @Override
    public String name() { return "repair_podman_socket"; }

    @Override
    public String description() {
        return "Repairs the rootless Podman socket by recreating /run/user/<uid> "
                + "with correct permissions and restarting the socket service. "
                + "Use when Podman fails with errors about a missing /run/user directory.";
    }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        ObjectNode result = MAPPER.createObjectNode();

        String uid  = execRaw("id -u").trim();
        String user = execRaw("id -un").trim();

        // Defensive: uid must be a numeric string to be safe to embed in a path
        if (!uid.matches("\\d+")) {
            throw new IllegalStateException("Unexpected uid value: " + uid);
        }
        String runUserDir = "/run/user/" + uid;

        result.put("uid", uid);
        result.put("user", user);
        result.put("runUserDir", runUserDir);

        // Record state before repair (use shell substitution to avoid injection)
        String before = execRaw("test -d /run/user/$(id -u) && echo exists || echo missing");
        result.put("dirBefore", before.trim());

        // Perform repair
        String repairOut = repairRunUserDir();
        result.put("repairOutput", repairOut);
        result.put("repairSuccess", repairOut.contains("REPAIR_OK"));

        // Verify Podman is now reachable
        String verify = execRaw("podman info >/dev/null 2>&1 && echo ok || echo fail");
        result.put("podmanStatus", verify.contains("ok") ? "running" : "unavailable");

        return result;
    }
}
