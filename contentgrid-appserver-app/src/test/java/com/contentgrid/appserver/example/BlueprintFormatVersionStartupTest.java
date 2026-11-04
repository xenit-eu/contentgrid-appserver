package com.contentgrid.appserver.example;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.json.exceptions.SchemaValidationException;
import com.contentgrid.appserver.registry.UnsupportedBlueprintFormatVersionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;

class BlueprintFormatVersionStartupTest {

    // This is not the format that we expect (entities should be an array)
    private static final String MALFORMED_APPLICATION_MODEL = """
            {
              "applicationName": "unsupported",
              "version": "1.0.0",
              "entities": {
                "person": {
                  "table": "person",
                  "pathSegment": "persons"
                }
              }
            }
            """;

    @TempDir
    Path blueprintArtifact;

    @Test
    void startupFailsOnUnsupportedBlueprintFormatVersion() throws IOException {
        Files.writeString(blueprintArtifact.resolve("application-model.json"), MALFORMED_APPLICATION_MODEL);
        Files.writeString(blueprintArtifact.resolve("manifest.json"), """
                { "format_version": 99999 }
                """);

        assertThatThrownBy(this::startApplication)
                .rootCause()
                .isInstanceOf(UnsupportedBlueprintFormatVersionException.class)
                .hasMessageContaining("has blueprint format version 99999, but this appserver only supports blueprint format versions up to");
    }

    @Test
    void startupFailsOnMalformedApplicationModelWithoutFormatVersion() throws IOException {
        // Makes sure that the test above does not pass just because the application model is never read
        Files.writeString(blueprintArtifact.resolve("application-model.json"), MALFORMED_APPLICATION_MODEL);

        assertThatThrownBy(this::startApplication)
                .rootCause()
                .isInstanceOf(SchemaValidationException.class);
    }

    private void startApplication() {
        new SpringApplicationBuilder(ContentgridApp.class)
                .profiles("bootRun")
                .run(
                        "--contentgrid.appserver.blueprint-artifact.location=file:" + blueprintArtifact,
                        "--server.port=0",
                        "--management.server.port=0"
                )
                .close();
    }
}
