package com.contentgrid.appserver.autoconfigure.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactException;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.impl.fs.classpath.ClassPathArtifact;
import java.nio.file.Path;
import java.util.List;
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

class InfrastructureAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(InfrastructureAutoConfiguration.class));

    @Test
    void defaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Artifact.class);
            assertThat(context.getBean(Artifact.class))
                    .isInstanceOfSatisfying(ClassPathArtifact.class, artifact ->
                            assertThat(artifact.getReference()).hasToString("classpath:."));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "classpath:my/location",
            "file:/my/path",
            "zip:/my/artifact.zip",
    })
    void withLocationProperty(String reference) {
        contextRunner
                .withPropertyValues("contentgrid.appserver.infrastructure.location=" + reference)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Artifact.class);
                    assertThat(context).getBean(Artifact.class).satisfies(artifact ->
                            assertThat(artifact.getReference()).hasToString(reference));
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
                .withPropertyValues("contentgrid.appserver.infrastructure.location=" + reference)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void withCustomArtifactBean() {
        contextRunner
                .withUserConfiguration(CustomArtifactConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("defaultArtifact");
                    assertThat(context).hasSingleBean(Artifact.class);
                });
    }

    @Configuration
    static class CustomArtifactConfiguration {

        @Bean
        Artifact customArtifact() {
            return new Artifact() {
                @Override
                public ArtifactReference getReference() {
                    return ArtifactReference.of("test", "custom");
                }

                @Override
                public ArtifactEntry load(Path path) throws ArtifactException {
                    return null;
                }

                @Override
                public List<ArtifactEntry> loadAll(Path path) throws ArtifactException {
                    return List.of();
                }
            };
        }
    }
}
