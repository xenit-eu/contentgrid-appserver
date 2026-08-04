package com.contentgrid.appserver.autoconfigure.blueprintartifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.autoconfigure.blueprintartifact.BlueprintArtifactAutoConfiguration.BlueprintArtifactProperties;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactItem;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import com.contentgrid.appserver.blueprintartifact.impl.fs.classpath.ClassPathBlueprintArtifact;
import com.contentgrid.appserver.autoconfigure.s3.S3BlueprintArtifactAutoConfiguration;
import com.contentgrid.appserver.blueprintartifact.impl.s3.S3BlueprintArtifactReferenceResolver;
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
            .withConfiguration(AutoConfigurations.of(BlueprintArtifactAutoConfiguration.class,
                    S3BlueprintArtifactAutoConfiguration.class));

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

    @Test
    void withS3_minimalValues() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.blueprint-artifact.s3.endpoint=http://localhost:9000",
                        "contentgrid.appserver.blueprint-artifact.s3.access-key=myAccessKey",
                        "contentgrid.appserver.blueprint-artifact.s3.secret-key=mySecretKey"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3BlueprintArtifactReferenceResolver.class);
                });
    }

    @Test
    void withS3_allValues() {
        contextRunner
                .withPropertyValues(
                        "contentgrid.appserver.blueprint-artifact.s3.endpoint=http://localhost:9000",
                        "contentgrid.appserver.blueprint-artifact.s3.access-key=myAccessKey",
                        "contentgrid.appserver.blueprint-artifact.s3.secret-key=mySecretKey",
                        "contentgrid.appserver.blueprint-artifact.s3.region=eu-west-1",
                        "contentgrid.appserver.blueprint-artifact.s3.path-style-access=false"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(BlueprintArtifactProperties.class);
                    assertThat(properties.s3().endpoint()).isEqualTo("http://localhost:9000");
                    assertThat(properties.s3().accessKey()).isEqualTo("myAccessKey");
                    assertThat(properties.s3().secretKey()).isEqualTo("mySecretKey");
                    assertThat(properties.s3().region()).isEqualTo("eu-west-1");
                    assertThat(properties.s3().pathStyleAccess()).isFalse();
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
