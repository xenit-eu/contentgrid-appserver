package com.contentgrid.appserver.registry;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class BlueprintFormatVersionValidator {

    // Update this when appserver becomes compatible with a new blueprint format. See BLUEPRINT_FORMAT_VERSION in Scribe.
    public static final int SUPPORTED_BLUEPRINT_FORMAT_VERSION = 1;

    private static final String FORMAT_VERSION_FIELD = "format_version";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public void validate(BlueprintArtifact blueprintArtifact) {
        try {
            blueprintArtifact.load(Path.of("manifest.json")).ifPresent(this::validate);
        } catch (BlueprintArtifactException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private void validate(BlueprintArtifactItem manifest) {
        var formatVersion = readTree(manifest).get(FORMAT_VERSION_FIELD);
        if (formatVersion == null) {
            return;
        }
        int version;
        try {
            version = formatVersion.intValue();
        } catch (JacksonException e) {
            throw new IllegalStateException("Blueprint manifest.json is malformed: '%s' must be an integer: %s"
                    .formatted(FORMAT_VERSION_FIELD, e.getOriginalMessage()), e);
        }
        if (version > SUPPORTED_BLUEPRINT_FORMAT_VERSION) {
            throw new UnsupportedBlueprintFormatVersionException(version, SUPPORTED_BLUEPRINT_FORMAT_VERSION);
        }
    }

    private static JsonNode readTree(BlueprintArtifactItem manifest) {
        JsonNode tree;
        try (InputStream input = manifest.getInputStream()) {
            tree = JSON_MAPPER.readTree(input);
        } catch (BlueprintArtifactItemUnreadableException | IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } catch (JacksonException e) {
            throw new IllegalStateException("Blueprint artifact item %s is malformed: %s"
                    .formatted(manifest.getItemReference(), e.getOriginalMessage()), e);
        }
        if (tree == null || !tree.isObject()) {
            throw new IllegalStateException("Blueprint artifact item %s is malformed: expected a JSON object"
                    .formatted(manifest.getItemReference()));
        }
        return tree;
    }
}
