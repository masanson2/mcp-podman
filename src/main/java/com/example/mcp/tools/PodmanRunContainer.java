package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;

/**
 * Runs a new Podman container with enforced resource limits and WSL2-safe defaults.
 *
 * <p>Mandatory resource flags applied to every invocation (unless overridden):
 * {@code --cpus}, {@code --memory}, {@code --memory-swap}, {@code --pids-limit}.
 *
 * <p>Optional {@code profile} values map to predefined resource sets:
 * <ul>
 *   <li>{@code jvm-backend} – 1.5 CPUs, 1024 MB memory, JVM container-awareness flags</li>
 *   <li>{@code frontend}    – 0.5 CPUs, 256 MB memory</li>
 *   <li>{@code db-dev}      – 1 CPU,   512 MB memory</li>
 *   <li>{@code infra}       – 0.5 CPUs, 256 MB memory</li>
 *   <li>{@code docmind}     – 2 CPUs, 2048 MB memory, preset image/ports/volumes/env for docmind</li>
 *   <li>{@code dbmind}      – 2 CPUs, 2048 MB memory, preset image/ports/volumes/env for dbmind</li>
 * </ul>
 */
public class PodmanRunContainer extends MCPTool {

    /** JVM options injected via JAVA_TOOL_OPTIONS for JVM-based containers. */
    static final String JVM_TOOL_OPTIONS =
            "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseG1GC";

    @Override
    public String name() { return "run_container"; }

    @Override
    public String description() {
        return "Run a new Podman container with enforced resource limits and WSL2-safe defaults. "
                + "Supports optional profiles: jvm-backend, frontend, db-dev, infra, docmind, dbmind. "
                + "The docmind and dbmind profiles supply a default image, ports, volumes and env vars "
                + "matching the production configuration; individual fields can still be overridden.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        props.putObject("image")
                .put("type", "string")
                .put("description", "Container image to run (e.g. nginx:latest)");

        props.putObject("name")
                .put("type", "string")
                .put("description", "Optional container name (--name)");

        props.putObject("profile")
                .put("type", "string")
                .put("description", "Resource profile: jvm-backend | frontend | db-dev | infra | docmind | dbmind");

        props.putObject("cpus")
                .put("type", "string")
                .put("description", "Override CPU limit (e.g. 2.0). Defaults to profile or 1");

        props.putObject("memory")
                .put("type", "string")
                .put("description", "Override memory limit (e.g. 512m). Defaults to profile or 512m");

        props.putObject("memory_swap")
                .put("type", "string")
                .put("description", "Override memory+swap limit. Defaults to same as memory");

        props.putObject("pids_limit")
                .put("type", "integer")
                .put("description", "Override pids limit. Default: 256");

        props.putObject("network")
                .put("type", "string")
                .put("description", "Network mode. Default: slirp4netns");

        props.putObject("ports")
                .put("type", "string")
                .put("description", "Port mappings, space-separated (e.g. '8080:80 8443:443')");

        props.putObject("volumes")
                .put("type", "string")
                .put("description", "Volume mounts, space-separated (e.g. '/home/user/data:/data')");

        props.putObject("env")
                .put("type", "string")
                .put("description", "Extra environment variables, space-separated (e.g. 'FOO=bar BAZ=qux')");

        props.putObject("detach")
                .put("type", "boolean")
                .put("description", "Run container in background (-d). Default: true");

        props.putObject("extra_args")
                .put("type", "string")
                .put("description", "Additional raw arguments appended to the podman run command");

        schema.putArray("required");
        return schema;
    }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        // ------------------------------------------------------------------ //
        // 1. Resolve resource profile (before image check so named flavours
        //    can supply a default image when none is provided by the caller)
        // ------------------------------------------------------------------ //
        String profile = input.path("profile").asText("").toLowerCase(Locale.ROOT);
        String defaultCpus;
        String defaultMemory;
        boolean isJvmProfile     = false;
        boolean isDocmindProfile = false;
        boolean isDbmindProfile  = false;

        switch (profile) {
            case "jvm-backend" -> { defaultCpus = "1.5"; defaultMemory = "1024m"; isJvmProfile = true; }
            case "frontend"    -> { defaultCpus = "0.5"; defaultMemory = "256m"; }
            case "db-dev"      -> { defaultCpus = "1";   defaultMemory = "512m"; }
            case "infra"       -> { defaultCpus = "0.5"; defaultMemory = "256m"; }
            case "docmind"     -> { defaultCpus = "2";   defaultMemory = "2048m"; isDocmindProfile = true; }
            case "dbmind"      -> { defaultCpus = "2";   defaultMemory = "2048m"; isDbmindProfile = true; }
            default            -> { defaultCpus = "1";   defaultMemory = "512m"; }
        }

        // ------------------------------------------------------------------ //
        // 2. Resolve image (named flavours supply a default when omitted)
        // ------------------------------------------------------------------ //
        String image = input.path("image").asText();
        if (image.isBlank()) {
            if (isDocmindProfile) {
                image = PodmanPullImage.DOCMIND_IMAGE;
            } else if (isDbmindProfile) {
                image = PodmanPullImage.DBMIND_IMAGE;
            } else {
                return MAPPER.getNodeFactory().textNode("ERROR: 'image' is required");
            }
        }

        String cpus        = nonBlank(input.path("cpus").asText(""),         defaultCpus);
        String memory      = nonBlank(input.path("memory").asText(""),       defaultMemory);
        String memorySwap  = nonBlank(input.path("memory_swap").asText(""),  memory); // swap disabled (memory-swap equals memory)
        int    pidsLimit   = input.path("pids_limit").asInt(256);
        String network     = nonBlank(input.path("network").asText(""),      "slirp4netns");
        boolean detach     = input.path("detach").asBoolean(true);

        // ------------------------------------------------------------------ //
        // 3. Bind-mount safety check for /mnt/c paths
        // ------------------------------------------------------------------ //
        String volumes = input.path("volumes").asText("");
        String mountWarning = "";
        if (volumes.contains("/mnt/c")) {
            mountWarning = "WARNING: bind mount under /mnt/c detected. "
                    + "Mounts from the Windows filesystem (/mnt/c) cause severe I/O degradation on WSL2. "
                    + "Use paths under /home instead. Container was NOT started.\n";
            return MAPPER.getNodeFactory().textNode(mountWarning);
        }

        // ------------------------------------------------------------------ //
        // 4. Build the podman run command
        // ------------------------------------------------------------------ //
        StringBuilder cmd = new StringBuilder("podman run");

        if (detach) {
            cmd.append(" -d");
        }

        // Container name (user-provided or profile preset)
        String containerName = input.path("name").asText("");
        if (containerName.isBlank()) {
            if (isDocmindProfile) containerName = "docmind";
            else if (isDbmindProfile) containerName = "dbmind";
        }
        if (!containerName.isBlank()) {
            cmd.append(" --name ").append(shellQuote(containerName));
        }

        // Mandatory resource limits
        cmd.append(" --cpus=").append(cpus);
        cmd.append(" --memory=").append(memory);
        cmd.append(" --memory-swap=").append(memorySwap);
        cmd.append(" --pids-limit=").append(pidsLimit);

        // Restart policy and log limits
        cmd.append(" --restart=on-failure:3");
        cmd.append(" --log-opt max-size=10m");
        cmd.append(" --log-opt max-file=3");

        // Network
        cmd.append(" --network=").append(network);

        // JVM profile: inject JAVA_TOOL_OPTIONS
        if (isJvmProfile) {
            cmd.append(" -e JAVA_TOOL_OPTIONS=").append(shellQuote(JVM_TOOL_OPTIONS));
        }

        // Port mappings (user-provided or profile preset)
        String ports = input.path("ports").asText("");
        if (ports.isBlank()) {
            if (isDocmindProfile) ports = "8008:8008";
            else if (isDbmindProfile) ports = "8009:8009";
        }
        if (!ports.isBlank()) {
            for (String p : ports.trim().split("\\s+")) {
                cmd.append(" -p ").append(shellQuote(p));
            }
        }

        // Volume mounts (user-provided or profile preset)
        if (!volumes.isBlank()) {
            for (String v : volumes.trim().split("\\s+")) {
                cmd.append(" -v ").append(shellQuote(v));
            }
        } else if (isDocmindProfile) {
            cmd.append(" -v 'docmind_pgdata:/var/lib/postgresql/data'");
            cmd.append(" -v 'docmind_fastembed_cache:/app/.fastembed'");
            cmd.append(" -v $HOME/.docmind:/root/.docmind");
        } else if (isDbmindProfile) {
            cmd.append(" -v 'dbmind_pgdata:/var/lib/postgresql/data'");
            cmd.append(" -v $HOME/.dbmind:/root/.dbmind");
        }

        // Extra environment variables (user-provided or profile preset)
        String env = input.path("env").asText("");
        if (!env.isBlank()) {
            for (String e : env.trim().split("\\s+")) {
                cmd.append(" -e ").append(shellQuote(e));
            }
        } else if (isDocmindProfile) {
            cmd.append(" -e 'PUBLIC_BASE_URL=http://localhost:8008'");
            cmd.append(" -e 'FTS_LANG=italian'");
            cmd.append(" -e 'ENABLE_CHAT=true'");
            cmd.append(" -e 'DOCMIND_DEFAULT_PROVIDER=copilot'");
            cmd.append(" -e GITHUB_TOKEN=$(gh auth token)");
        } else if (isDbmindProfile) {
            cmd.append(" -e 'PUBLIC_BASE_URL=http://localhost:8009'");
            cmd.append(" -e 'FTS_LANG=italian'");
            cmd.append(" -e GITHUB_TOKEN=$(gh auth token)");
        }

        // Additional raw arguments
        String extraArgs = input.path("extra_args").asText("");
        if (!extraArgs.isBlank()) {
            cmd.append(" ").append(extraArgs);
        }

        // Image
        cmd.append(" ").append(shellQuote(image));

        String result = exec(cmd.toString());
        return MAPPER.getNodeFactory().textNode(result);
    }

    /** Returns {@code value} when non-blank, otherwise {@code fallback}. */
    private static String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    /**
     * Wraps a value in single quotes and escapes any embedded single quotes.
     * Safe for shell arguments that must not undergo further word-splitting.
     */
    static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
