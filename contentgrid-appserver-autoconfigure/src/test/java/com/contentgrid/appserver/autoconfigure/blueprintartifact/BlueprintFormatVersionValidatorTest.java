package com.contentgrid.appserver.autoconfigure.blueprintartifact;

import static com.contentgrid.appserver.autoconfigure.blueprintartifact.BlueprintFormatVersionValidator.SUPPORTED_BLUEPRINT_FORMAT_VERSIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.blueprintartifact.impl.fs.directory.FilesystemDirectoryBlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BlueprintFormatVersionValidatorTest {

    private final BlueprintFormatVersionValidator validator = new BlueprintFormatVersionValidator();

    @TempDir
    Path directory;

    @Test
    void withoutManifest() {
        assertThatCode(() -> validator.validate(blueprintArtifact())).doesNotThrowAnyException();
    }

    @Test
    void withoutFormatVersion() throws IOException {
        var blueprintArtifact = blueprintArtifactWithManifest("""
                { "project": "dem" }
                """);

        assertThatCode(() -> validator.validate(blueprintArtifact)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @FieldSource("com.contentgrid.appserver.autoconfigure.blueprintartifact.BlueprintFormatVersionValidator#SUPPORTED_BLUEPRINT_FORMAT_VERSIONS")
    void withSupportedFormatVersion(int formatVersion) throws IOException {
        var blueprintArtifact = blueprintArtifactWithManifest("""
                { "format_version": %d }
                """.formatted(formatVersion));

        assertThatCode(() -> validator.validate(blueprintArtifact)).doesNotThrowAnyException();
    }

    static IntStream unsupportedFormatVersions() {
        var newest = Collections.max(SUPPORTED_BLUEPRINT_FORMAT_VERSIONS);
        var oldest = Collections.min(SUPPORTED_BLUEPRINT_FORMAT_VERSIONS);
        // Newer than the newest supported version, and older than the oldest one that is still supported
        return IntStream.of(newest + 1, oldest - 1);
    }

    @ParameterizedTest
    @MethodSource("unsupportedFormatVersions")
    void withUnsupportedFormatVersion(int formatVersion) throws IOException {
        var blueprintArtifact = blueprintArtifactWithManifest("""
                { "format_version": %d }
                """.formatted(formatVersion));

        assertThatThrownBy(() -> validator.validate(blueprintArtifact))
                .isInstanceOfSatisfying(UnsupportedBlueprintFormatVersionException.class, e -> {
                    assertThat(e.getFormatVersion()).isEqualTo(formatVersion);
                    assertThat(e.getSupportedFormatVersions())
                            .containsExactlyInAnyOrderElementsOf(SUPPORTED_BLUEPRINT_FORMAT_VERSIONS);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{ \"format_version\": \"1\" }",
            "{ \"format_version\": 1.5 }",
            "{ \"format_version\": null }",
            "{ \"format_version\": 99999999999 }",
    })
    void withInvalidFormatVersion(String manifest) throws IOException {
        var blueprintArtifact = blueprintArtifactWithManifest(manifest);

        assertThatThrownBy(() -> validator.validate(blueprintArtifact))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manifest.json is malformed: 'format_version' must be an integer");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "{ \"format_version\": ",
            "[]",
            "1",
    })
    void withMalformedManifest(String manifest) throws IOException {
        var blueprintArtifact = blueprintArtifactWithManifest(manifest);

        assertThatThrownBy(() -> validator.validate(blueprintArtifact))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("manifest.json is malformed");
    }

    private BlueprintArtifact blueprintArtifact() {
        return new FilesystemDirectoryBlueprintArtifact(directory);
    }

    private BlueprintArtifact blueprintArtifactWithManifest(String manifest) throws IOException {
        Files.writeString(directory.resolve("manifest.json"), manifest);
        return blueprintArtifact();
    }
}
