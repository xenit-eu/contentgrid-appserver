package com.contentgrid.appserver.autoconfigure.blueprintartifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import com.contentgrid.appserver.blueprintartifact.impl.fs.classpath.ClassPathBlueprintArtifact;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class BlueprintArtifactAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(BlueprintArtifactAutoConfiguration.class));

    @Test
    void defaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BlueprintArtifact.class);
            assertThat(context.getBean(BlueprintArtifact.class))
                    .isInstanceOfSatisfying(ClassPathBlueprintArtifact.class, blueprintArtifact ->
                            assertThat(blueprintArtifact.getReference()).hasToString("classpath:."));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "classpath:my/location",
            "file:/my/path",
            "zip:/my/blueprint-artifact.zip",
    })
    void withLocationProperty(String reference) {
        contextRunner
                .withPropertyValues("contentgrid.appserver.blueprint-artifact.location=" + reference)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(BlueprintArtifact.class);
                    assertThat(context).getBean(BlueprintArtifact.class).satisfies(blueprintArtifact ->
                            assertThat(blueprintArtifact.getReference()).hasToString(reference));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file/my/path",
            "unknown:my/location",
            "\"\"",
    })
    void withInvalidLocationProperty(String reference) {
        contextRunner
                .withPropertyValues("contentgrid.appserver.blueprint-artifact.location=" + reference)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void withCustomBlueprintArtifactBean() {
        contextRunner
                .withUserConfiguration(CustomBlueprintArtifactConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("defaultBlueprintArtifact");
                    assertThat(context).hasSingleBean(BlueprintArtifact.class);
                });
    }

    @Configuration
    static class CustomBlueprintArtifactConfiguration {

        @Bean
        BlueprintArtifact customBlueprintArtifact() {
            return new BlueprintArtifact() {
                @Override
                public BlueprintArtifactReference getReference() {
                    return BlueprintArtifactReference.of("test:custom");
                }

                @Override
                public Optional<BlueprintArtifactItem> load(Path path) {
                    return Optional.empty();
                }

                @Override
                public List<BlueprintArtifactItem> loadAll(Path path) {
                    return List.of();
                }
            };
        }
    }
}
