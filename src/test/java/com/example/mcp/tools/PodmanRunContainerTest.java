package com.example.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PodmanRunContainer} — command-building logic only.
 * No Podman installation is required.
 */
class PodmanRunContainerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------ //
    // Helper: build an input ObjectNode from key/value pairs
    // ------------------------------------------------------------------ //
    private ObjectNode buildInputNode(Object... pairs) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = (String) pairs[i];
            Object val = pairs[i + 1];
            if (val instanceof String s) node.put(key, s);
            else if (val instanceof Boolean b) node.put(key, b);
            else if (val instanceof Integer iv) node.put(key, iv);
        }
        return node;
    }

    // ------------------------------------------------------------------ //
    // Default resource limits
    // ------------------------------------------------------------------ //

    @Test
    void defaultLimitsAreApplied() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest"));
        assertTrue(cmd.contains("--cpus=1"),         "default cpus");
        assertTrue(cmd.contains("--memory=512m"),    "default memory");
        assertTrue(cmd.contains("--memory-swap=512m"), "default memory-swap equals memory");
        assertTrue(cmd.contains("--pids-limit=256"), "default pids-limit");
    }

    @Test
    void safeDefaultsAreAlwaysPresent() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest"));
        assertTrue(cmd.contains("--restart=on-failure:3"),  "restart policy");
        assertTrue(cmd.contains("--log-opt max-size=10m"),  "log max-size");
        assertTrue(cmd.contains("--log-opt max-file=3"),    "log max-file");
    }

    @Test
    void defaultNetworkIsSlirp4netns() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest"));
        assertTrue(cmd.contains("--network=slirp4netns"), "default network");
    }

    // ------------------------------------------------------------------ //
    // Profile resolution
    // ------------------------------------------------------------------ //

    @Test
    void jvmBackendProfileAppliesCorrectLimits() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "openjdk:21", "profile", "jvm-backend"));
        assertTrue(cmd.contains("--cpus=1.5"),          "jvm-backend cpus");
        assertTrue(cmd.contains("--memory=1024m"),      "jvm-backend memory");
        assertTrue(cmd.contains("--memory-swap=1024m"), "jvm-backend memory-swap");
    }

    @Test
    void jvmBackendProfileInjectsJavaToolOptions() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "openjdk:21", "profile", "jvm-backend"));
        assertTrue(cmd.contains("JAVA_TOOL_OPTIONS"),                  "JAVA_TOOL_OPTIONS env key");
        assertTrue(cmd.contains("UseContainerSupport"),                "UseContainerSupport flag");
        assertTrue(cmd.contains("MaxRAMPercentage=75.0"),              "MaxRAMPercentage flag");
        assertTrue(cmd.contains("InitialRAMPercentage=50.0"),          "InitialRAMPercentage flag");
        assertTrue(cmd.contains("UseG1GC"),                            "UseG1GC flag");
    }

    @Test
    void frontendProfileAppliesCorrectLimits() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "node:20", "profile", "frontend"));
        assertTrue(cmd.contains("--cpus=0.5"),       "frontend cpus");
        assertTrue(cmd.contains("--memory=256m"),    "frontend memory");
        assertFalse(cmd.contains("JAVA_TOOL_OPTIONS"), "no JVM opts for frontend");
    }

    @Test
    void dbDevProfileAppliesCorrectLimits() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "postgres:16", "profile", "db-dev"));
        assertTrue(cmd.contains("--cpus=1"),       "db-dev cpus");
        assertTrue(cmd.contains("--memory=512m"),  "db-dev memory");
    }

    @Test
    void infraProfileAppliesCorrectLimits() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "redis:7", "profile", "infra"));
        assertTrue(cmd.contains("--cpus=0.5"),      "infra cpus");
        assertTrue(cmd.contains("--memory=256m"),   "infra memory");
    }

    // ------------------------------------------------------------------ //
    // docmind / dbmind profiles
    // ------------------------------------------------------------------ //

    @Test
    void docmindProfileAppliesCorrectLimits() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind"));
        assertTrue(cmd.contains("--cpus=2"),         "docmind cpus");
        assertTrue(cmd.contains("--memory=2048m"),   "docmind memory");
    }

    @Test
    void docmindProfileUsesDefaultImage() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind"));
        assertTrue(cmd.contains("docmind:latest"), "docmind default image");
    }

    @Test
    void docmindProfileUsesDefaultName() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind"));
        assertTrue(cmd.contains("--name 'docmind'"), "docmind default container name");
    }

    @Test
    void docmindProfileUsesDefaultPort() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind"));
        assertTrue(cmd.contains("-p '8008:8008'"), "docmind default port");
    }

    @Test
    void docmindProfileUsesDefaultVolumes() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind"));
        assertTrue(cmd.contains("docmind_pgdata:/var/lib/postgresql/data"), "docmind pgdata volume");
        assertTrue(cmd.contains("docmind_fastembed_cache:/app/.fastembed"), "docmind fastembed volume");
        assertTrue(cmd.contains("$HOME/.docmind:/root/.docmind"), "docmind home volume with $HOME");
    }

    @Test
    void docmindProfileUsesDefaultEnv() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind"));
        assertTrue(cmd.contains("PUBLIC_BASE_URL=http://localhost:8008"), "docmind PUBLIC_BASE_URL");
        assertTrue(cmd.contains("FTS_LANG=italian"),                      "docmind FTS_LANG");
        assertTrue(cmd.contains("ENABLE_CHAT=true"),                      "docmind ENABLE_CHAT");
        assertTrue(cmd.contains("DOCMIND_DEFAULT_PROVIDER=copilot"),      "docmind provider");
        assertTrue(cmd.contains("GITHUB_TOKEN=$(gh auth token)"),         "docmind GITHUB_TOKEN with substitution");
    }

    @Test
    void docmindProfileImageCanBeOverridden() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind",
                "image", "registry.local/docmind-custom:dev"));
        assertTrue(cmd.contains("registry.local/docmind-custom:dev"), "custom image overrides profile default");
    }

    @Test
    void docmindProfilePortCanBeOverridden() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind", "ports", "9090:8008"));
        assertTrue(cmd.contains("-p '9090:8008'"),  "user-supplied port must be used");
        assertFalse(cmd.contains("-p '8008:8008'"), "default port must not appear when overridden");
    }

    @Test
    void docmindProfileEnvCanBeOverridden() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "docmind", "env", "MY_VAR=1"));
        assertTrue(cmd.contains("-e 'MY_VAR=1'"),             "user env must be used");
        assertFalse(cmd.contains("DOCMIND_DEFAULT_PROVIDER"), "default env must not appear when overridden");
    }

    @Test
    void dbmindProfileAppliesCorrectLimits() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "dbmind"));
        assertTrue(cmd.contains("--cpus=2"),       "dbmind cpus");
        assertTrue(cmd.contains("--memory=2048m"), "dbmind memory");
    }

    @Test
    void dbmindProfileUsesDefaultImage() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "dbmind"));
        assertTrue(cmd.contains("dbmind:latest"), "dbmind default image");
    }

    @Test
    void dbmindProfileUsesDefaultPort() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "dbmind"));
        assertTrue(cmd.contains("-p '8009:8009'"), "dbmind default port");
    }

    @Test
    void dbmindProfileUsesGithubTokenSubstitution() throws Exception {
        String cmd = buildCmd(buildInputNode("profile", "dbmind"));
        assertTrue(cmd.contains("GITHUB_TOKEN=$(gh auth token)"), "dbmind GITHUB_TOKEN with substitution");
    }

    // ------------------------------------------------------------------ //
    // Override behaviour
    // ------------------------------------------------------------------ //

    @Test
    void explicitCpuOverridesProfile() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "cpus", "2.0"));
        assertTrue(cmd.contains("--cpus=2.0"), "explicit cpu override");
    }

    @Test
    void explicitMemoryOverridesDefault() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "memory", "256m"));
        assertTrue(cmd.contains("--memory=256m"), "explicit memory override");
    }

    @Test
    void explicitNetworkOverridesDefault() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "network", "host"));
        assertTrue(cmd.contains("--network=host"), "explicit network override");
    }

    @Test
    void explicitPidsLimitOverridesDefault() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "pids_limit", 512));
        assertTrue(cmd.contains("--pids-limit=512"), "explicit pids_limit override");
    }

    @Test
    void memorySwapDefaultsToMemoryValue() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "memory", "768m"));
        assertTrue(cmd.contains("--memory-swap=768m"), "memory-swap mirrors memory");
    }

    @Test
    void explicitMemorySwapOverrideIsRespected() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "memory", "512m", "memory_swap", "1024m"));
        assertTrue(cmd.contains("--memory-swap=1024m"), "explicit memory_swap override");
    }

    // ------------------------------------------------------------------ //
    // Bind-mount safety
    // ------------------------------------------------------------------ //

    @Test
    void mntCMountIsBlockedWithWarning() throws Exception {
        ObjectNode node = buildInputNode("image", "nginx:latest", "volumes", "/mnt/c/data:/data");
        // Subclass to intercept run without executing real podman
        String result = captureRunResult(node);
        assertTrue(result.startsWith("WARNING:"), "must emit a WARNING");
        assertTrue(result.contains("/mnt/c"),     "must mention /mnt/c");
        assertFalse(result.contains("podman run"), "container must NOT be started");
    }

    @Test
    void homeMountIsAllowed() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "volumes", "/home/user/data:/data"));
        assertTrue(cmd.contains("-v '/home/user/data:/data'"), "home mount is permitted");
    }

    // ------------------------------------------------------------------ //
    // Misc argument handling
    // ------------------------------------------------------------------ //

    @Test
    void containerNameIsIncluded() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "name", "my-nginx"));
        assertTrue(cmd.contains("--name 'my-nginx'"), "container name");
    }

    @Test
    void detachFlagDefaultsToTrue() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest"));
        assertTrue(cmd.contains(" -d ") || cmd.startsWith("podman run -d"), "detach by default");
    }

    @Test
    void detachCanBeDisabled() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "detach", false));
        assertFalse(cmd.contains(" -d"), "no detach flag when detach=false");
    }

    @Test
    void portMappingsAreAdded() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "ports", "8080:80 8443:443"));
        assertTrue(cmd.contains("-p '8080:80'"),   "port 8080:80");
        assertTrue(cmd.contains("-p '8443:443'"),  "port 8443:443");
    }

    @Test
    void environmentVariablesAreAdded() throws Exception {
        String cmd = buildCmd(buildInputNode("image", "nginx:latest", "env", "FOO=bar BAZ=qux"));
        assertTrue(cmd.contains("-e 'FOO=bar'"), "env FOO");
        assertTrue(cmd.contains("-e 'BAZ=qux'"), "env BAZ");
    }

    @Test
    void imageIsMissingReturnsError() throws Exception {
        String result = captureRunResult(MAPPER.createObjectNode());
        assertTrue(result.startsWith("ERROR:"), "must return an error for missing image");
    }

    @Test
    void shellQuoteHandlesEmbeddedSingleQuotes() {
        String quoted = PodmanRunContainer.shellQuote("it's a test");
        assertEquals("'it'\\''s a test'", quoted);
    }

    @Test
    void shellQuoteHandlesPlainValue() {
        assertEquals("'nginx:latest'", PodmanRunContainer.shellQuote("nginx:latest"));
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    /**
     * Returns the command string that would be passed to the shell
     * by constructing a test-only subclass that captures the command
     * instead of executing it.
     */
    private String buildCmd(ObjectNode node) throws Exception {
        CommandCapturingRunContainer tool = new CommandCapturingRunContainer();
        tool.run(node);
        return tool.capturedCommand;
    }

    /** Returns the textual result of {@link PodmanRunContainer#run} without executing shell commands. */
    private String captureRunResult(ObjectNode node) throws Exception {
        CommandCapturingRunContainer tool = new CommandCapturingRunContainer();
        com.fasterxml.jackson.databind.JsonNode result = tool.run(node);
        return result.asText();
    }

    /**
     * Subclass that overrides {@code exec} to capture the command
     * instead of running it, enabling pure-unit tests.
     */
    static class CommandCapturingRunContainer extends PodmanRunContainer {
        String capturedCommand = "";

        @Override
        protected String exec(String cmd) {
            capturedCommand = cmd;
            return "mock-container-id";
        }
    }
}
