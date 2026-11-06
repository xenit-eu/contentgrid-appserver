package com.contentgrid.appserver.registry;

import static com.contentgrid.appserver.registry.BlueprintFormatVersionValidator.SUPPORTED_BLUEPRINT_FORMAT_VERSION;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.blueprintartifact.impl.fs.directory.FilesystemDirectoryBlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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
    @ValueSource(ints = {SUPPORTED_BLUEPRINT_FORMAT_VERSION - 1, SUPPORTED_BLUEPRINT_FORMAT_VERSION})
    void withSupportedFormatVersion(int formatVersion) throws IOException {
        var blueprintArtifact = blueprintArtifactWithManifest("""
                { "format_version": %d }
                """.formatted(formatVersion));

        assertThatCode(() -> validator.validate(blueprintArtifact)).doesNotThrowAnyException();
    }

    @Test
    void withUnsupportedFormatVersion() throws IOException {
        var formatVersion = SUPPORTED_BLUEPRINT_FORMAT_VERSION + 1;
        var blueprintArtifact = blueprintArtifactWithManifest("""
                { "format_version": %d }
                """.formatted(formatVersion));

        assertThatThrownBy(() -> validator.validate(blueprintArtifact))
                .isInstanceOf(UnsupportedBlueprintFormatVersionException.class)
                .hasMessage("Blueprint manifest has blueprint format version %d, but this appserver only supports "
                                + "blueprint format versions up to %d. Upgrade the appserver to a version that supports "
                                + "blueprint format version %d.", formatVersion, SUPPORTED_BLUEPRINT_FORMAT_VERSION,
                        formatVersion);
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
