package com.example.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PodmanPullImage} — command-building logic only.
 * No Podman installation is required.
 */
class PodmanPullImageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ------------------------------------------------------------------ //
    // Named flavour resolution
    // ------------------------------------------------------------------ //

    @Test
    void docmindFlavourPullsCorrectImage() throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("flavour", "docmind");
        String cmd = buildCmd(node);
        assertTrue(cmd.contains("podman pull"), "must be a pull command");
        assertTrue(cmd.contains(PodmanPullImage.DOCMIND_IMAGE), "must pull docmind image");
    }

    @Test
    void dbmindFlavourPullsCorrectImage() throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("flavour", "dbmind");
        String cmd = buildCmd(node);
        assertTrue(cmd.contains("podman pull"), "must be a pull command");
        assertTrue(cmd.contains(PodmanPullImage.DBMIND_IMAGE), "must pull dbmind image");
    }

    @Test
    void flavourIsCaseInsensitive() throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("flavour", "DOCMIND");
        String cmd = buildCmd(node);
        assertTrue(cmd.contains(PodmanPullImage.DOCMIND_IMAGE), "flavour lookup must be case-insensitive");
    }

    // ------------------------------------------------------------------ //
    // Explicit image reference
    // ------------------------------------------------------------------ //

    @Test
    void explicitImageIsPulled() throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("image", "registry.example.com/myapp:2.0");
        String cmd = buildCmd(node);
        assertTrue(cmd.contains("podman pull"), "must be a pull command");
        assertTrue(cmd.contains("registry.example.com/myapp:2.0"), "must pull the specified image");
    }

    @Test
    void flavourTakesPrecedenceOverImage() throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("flavour", "docmind");
        node.put("image", "some-other-image:latest");
        String cmd = buildCmd(node);
        assertTrue(cmd.contains(PodmanPullImage.DOCMIND_IMAGE), "flavour must win over image field");
        assertFalse(cmd.contains("some-other-image"), "explicit image must be ignored when flavour is set");
    }

    // ------------------------------------------------------------------ //
    // Error handling
    // ------------------------------------------------------------------ //

    @Test
    void missingFlavourAndImageReturnsError() throws Exception {
        String result = captureRunResult(MAPPER.createObjectNode());
        assertTrue(result.startsWith("ERROR:"), "must return an error");
        assertTrue(result.contains("flavour"), "error must mention flavour");
        assertTrue(result.contains("image"),   "error must mention image");
    }

    // ------------------------------------------------------------------ //
    // Internal helpers
    // ------------------------------------------------------------------ //

    private String buildCmd(ObjectNode node) throws Exception {
        CommandCapturingPullImage tool = new CommandCapturingPullImage();
        tool.run(node);
        return tool.capturedCommand;
    }

    private String captureRunResult(ObjectNode node) throws Exception {
        CommandCapturingPullImage tool = new CommandCapturingPullImage();
        com.fasterxml.jackson.databind.JsonNode result = tool.run(node);
        return result.asText();
    }

    static class CommandCapturingPullImage extends PodmanPullImage {
        String capturedCommand = "";

        @Override
        protected String exec(String cmd) {
            capturedCommand = cmd;
            return "mock-digest";
        }
    }
}
