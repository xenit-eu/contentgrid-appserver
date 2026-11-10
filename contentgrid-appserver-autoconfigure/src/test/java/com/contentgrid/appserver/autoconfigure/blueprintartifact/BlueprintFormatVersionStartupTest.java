package com.contentgrid.appserver.autoconfigure.blueprintartifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.json.exceptions.SchemaValidationException;
import com.contentgrid.appserver.autoconfigure.json.schema.ApplicationResolverAutoConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BlueprintFormatVersionStartupTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withConfiguration(AutoConfigurations.of(
                    BlueprintArtifactAutoConfiguration.class,
                    ApplicationResolverAutoConfiguration.class
            ));

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

        startApplication().run(context -> assertThat(context).getFailure()
                .rootCause()
                .isInstanceOf(UnsupportedBlueprintFormatVersionException.class)
                .hasMessageContaining("has blueprint format version 99999, but this appserver only supports blueprint format versions ["));
    }

    @Test
    void startupFailsOnMalformedApplicationModelWithoutFormatVersion() throws IOException {
        // Makes sure that the test above does not pass just because the application model is never read
        Files.writeString(blueprintArtifact.resolve("application-model.json"), MALFORMED_APPLICATION_MODEL);

        startApplication().run(context -> assertThat(context).getFailure()
                .rootCause()
                .isInstanceOf(SchemaValidationException.class));
    }

    private ApplicationContextRunner startApplication() {
        return contextRunner
                .withPropertyValues("contentgrid.appserver.blueprint-artifact.location=file:" + blueprintArtifact);
    }
}
