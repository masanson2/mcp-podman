package com.example.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PodmanListContainers} — focusing on resilience when
 * Podman emits text warnings before the JSON payload.
 */
class PodmanListContainersTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------ //
    // stripNonJsonPrefix — exposed via helper subclass for white-box tests
    // ------------------------------------------------------------------ //

    @Test
    void stripNonJsonPrefix_cleanArrayPassesThrough() {
        String input = "[{\"Id\":\"abc\"}]";
        assertEquals(input, helper().strip(input));
    }

    @Test
    void stripNonJsonPrefix_cleanObjectPassesThrough() {
        String input = "{\"key\":\"value\"}";
        assertEquals(input, helper().strip(input));
    }

    @Test
    void stripNonJsonPrefix_stripsLeadingWarningLine() {
        String input = "time=\"2024-01-01T00:00:00Z\" level=warning msg=\"no systemd user session\"\n[{\"Id\":\"abc\"}]";
        String result = helper().strip(input);
        assertTrue(result.startsWith("["), "must start with JSON array bracket");
        assertTrue(result.contains("\"Id\":\"abc\""), "JSON content must be preserved");
    }

    @Test
    void stripNonJsonPrefix_stripsMultipleLeadingWarningLines() {
        String input = "WARN[0000] msg=first warning\nWARN[0001] msg=second warning\n[{\"State\":\"running\"}]";
        String result = helper().strip(input);
        assertTrue(result.startsWith("["), "must start with JSON array bracket");
    }

    @Test
    void stripNonJsonPrefix_stripsLogrusStyleWarning() {
        String logrusWarning = "time=\"2024-03-15T10:00:00.123456789+01:00\" level=warning "
                + "msg=\"'overlay' is not supported over overlayfs, using 'vfs' driver\"\n";
        String json = "[{\"Names\":[\"docmind\"],\"State\":\"running\"}]";
        String result = helper().strip(logrusWarning + json);
        assertTrue(result.startsWith("["), "JSON array must be found after logrus warning");
        assertTrue(result.contains("docmind"), "container name must be preserved");
    }

    @Test
    void stripNonJsonPrefix_noJsonReturnsOriginal() {
        String input = "Error: no such container: notfound";
        assertEquals(input, helper().strip(input));
    }

    @Test
    void stripNonJsonPrefix_nullReturnsNull() {
        assertNull(helper().strip(null));
    }

    @Test
    void stripNonJsonPrefix_blankReturnsBlank() {
        assertEquals("  ", helper().strip("  "));
    }

    // ------------------------------------------------------------------ //
    // list_containers — runs correctly when output has warning prefix
    // ------------------------------------------------------------------ //

    @Test
    void listContainers_parsesCleanJsonArray() throws Exception {
        String mockOutput = "[{\"Names\":[\"nginx\"],\"State\":\"running\"}]";
        JsonNode result = runWithMockOutput(mockOutput, false);
        assertTrue(result.isArray(), "result must be an array");
        assertEquals(1, result.size());
        assertEquals("nginx", result.get(0).path("Names").get(0).asText());
    }

    @Test
    void listContainers_parsesJsonPrecededByWarnings() throws Exception {
        String mockOutput = "time=\"2024-01-01T00:00:00Z\" level=warning msg=\"cgroupfs\"\n"
                + "[{\"Names\":[\"docmind\"],\"State\":\"running\"}]";
        JsonNode result = runWithMockOutput(mockOutput, false);
        assertTrue(result.isArray(), "result must be an array even with leading warnings");
        assertEquals("docmind", result.get(0).path("Names").get(0).asText());
    }

    @Test
    void listContainers_emptyOutputReturnsEmptyArray() throws Exception {
        JsonNode result = runWithMockOutput("", false);
        assertTrue(result.isArray());
        assertEquals(0, result.size());
    }

    @Test
    void listContainers_warningsOnlyOutputThrowsParseException() {
        // Output contains only warnings, no JSON — a real Podman failure. The
        // parse exception propagates to ToolRegistry, which converts it to a
        // well-formed JSON-RPC error response rather than silently returning [].
        String mockOutput = "WARN[0000] no systemd user session available, using cgroupfs";
        assertThrows(Exception.class, () -> runWithMockOutput(mockOutput, false),
                "should propagate parse exception for non-JSON output so the caller sees a real error");
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    private JsonNode runWithMockOutput(String mockOutput, boolean runningOnly) throws Exception {
        MockListContainers tool = new MockListContainers(mockOutput);
        ObjectNode input = MAPPER.createObjectNode();
        input.put("running_only", runningOnly);
        return tool.run(input);
    }

    /** Returns a helper instance that exposes the protected stripNonJsonPrefix method. */
    private static MockListContainers helper() {
        return new MockListContainers("");
    }

    static class MockListContainers extends PodmanListContainers {
        private final String mockOutput;

        MockListContainers(String mockOutput) {
            this.mockOutput = mockOutput;
        }

        @Override
        protected String exec(String cmd) {
            return mockOutput;
        }

        /** Exposes the protected method for direct white-box testing. */
        String strip(String output) {
            return stripNonJsonPrefix(output);
        }
    }
}

