package com.example.mcp.tools;

import com.example.mcp.MCPTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;

/**
 * Pulls a Podman image from a registry, updating it to the latest available version.
 *
 * <p>Supports named flavours that expand to well-known production images:
 * <ul>
 *   <li>{@code docmind} – production.eng.it:8433/intelligence_automation_asset/docmind:latest</li>
 *   <li>{@code dbmind}  – production.eng.it:8433/intelligence_automation_asset/dbmind:latest</li>
 * </ul>
 */
public class PodmanPullImage extends MCPTool {

    static final String DOCMIND_IMAGE =
            "production.eng.it:8433/intelligence_automation_asset/docmind:latest";
    static final String DBMIND_IMAGE =
            "production.eng.it:8433/intelligence_automation_asset/dbmind:latest";

    @Override
    public String name() { return "pull_image"; }

    @Override
    public String description() {
        return "Pull (download / update to latest) a Podman image from a registry. "
                + "Use flavour='docmind' or flavour='dbmind' for the pre-configured production images, "
                + "or supply a fully-qualified image reference in the 'image' field.";
    }

    @Override
    public ObjectNode inputSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");

        props.putObject("flavour")
                .put("type", "string")
                .put("description",
                        "Named flavour to pull: docmind | dbmind. Takes precedence over 'image'.");

        props.putObject("image")
                .put("type", "string")
                .put("description",
                        "Fully-qualified image reference to pull (e.g. registry/org/app:latest). "
                        + "Ignored when 'flavour' is set.");

        return schema;
    }

    @Override
    public JsonNode run(JsonNode input) throws Exception {
        String flavour = input.path("flavour").asText("").toLowerCase(Locale.ROOT);
        String image;

        switch (flavour) {
            case "docmind" -> image = DOCMIND_IMAGE;
            case "dbmind"  -> image = DBMIND_IMAGE;
            default -> {
                image = input.path("image").asText("");
                if (image.isBlank()) {
                    return MAPPER.getNodeFactory().textNode(
                            "ERROR: either 'flavour' (docmind|dbmind) or 'image' is required");
                }
            }
        }

        String result = exec("podman pull " + PodmanRunContainer.shellQuote(image));
        return MAPPER.getNodeFactory().textNode(result);
    }
}
