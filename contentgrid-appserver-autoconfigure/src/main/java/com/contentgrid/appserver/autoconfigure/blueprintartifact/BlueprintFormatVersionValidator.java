package com.contentgrid.appserver.autoconfigure.blueprintartifact;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactException;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItemUnreadableException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class BlueprintFormatVersionValidator {

    // Update this when appserver becomes compatible with a new blueprint format. See BLUEPRINT_FORMAT_VERSION in Scribe.
    // 0 is a special case that refers to blueprints _without_ a `format_version` field (or without a manifest.json).
    public static final Set<Integer> SUPPORTED_BLUEPRINT_FORMAT_VERSIONS = Set.of(0, 1);

    private static final String FORMAT_VERSION_FIELD = "format_version";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    public void validate(BlueprintArtifact blueprintArtifact)
            throws BlueprintArtifactException, BlueprintArtifactItemUnreadableException {
        var manifest = blueprintArtifact.load(Path.of("manifest.json"));
        int version = 0;
        if (manifest.isPresent()) {
            version = readFormatVersionField(manifest.get()).orElse(0);
        }
        if (!SUPPORTED_BLUEPRINT_FORMAT_VERSIONS.contains(version)) {
            throw new UnsupportedBlueprintFormatVersionException(version, SUPPORTED_BLUEPRINT_FORMAT_VERSIONS);
        }
    }

    private static Optional<Integer> readFormatVersionField(BlueprintArtifactItem manifest)
            throws BlueprintArtifactItemUnreadableException {
        JsonNode tree;
        try (InputStream input = manifest.getInputStream()) {
            tree = JSON_MAPPER.readTree(input);
        } catch (IOException e) {
            throw new BlueprintArtifactItemUnreadableException(manifest.getItemReference(), e);
        } catch (JacksonException e) {
            throw new IllegalStateException("Blueprint artifact item %s is malformed: %s"
                    .formatted(manifest.getItemReference(), e.getOriginalMessage()), e);
        }
        if (tree == null || !tree.isObject()) {
            throw new IllegalStateException("Blueprint artifact item %s is malformed: expected a JSON object"
                    .formatted(manifest.getItemReference()));
        }
        var formatVersion = tree.get(FORMAT_VERSION_FIELD);
        if (formatVersion == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(formatVersion.intValue());
        } catch (JacksonException e) {
            throw new IllegalStateException("Blueprint manifest.json is malformed: '%s' must be an integer: %s"
                    .formatted(FORMAT_VERSION_FIELD, e.getOriginalMessage()), e);
        }
    }
}
