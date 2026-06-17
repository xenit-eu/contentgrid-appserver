package com.contentgrid.appserver.autoconfigure.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.autoconfigure.blueprintartifact.BlueprintArtifactAutoConfiguration;
import com.contentgrid.common.spring.autoconfigure.FlywayPostgresAutoConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class FlywayAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration.class,
                    FlywayAutoConfiguration.class, FlywayPostgresAutoConfiguration.class,
                    BlueprintArtifactAutoConfiguration.class
            ));

    @Test
    void picksUpMigrationFiles() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:tc:postgresql:15:///",
                        "contentgrid.appserver.blueprint-artifact.location=classpath:blueprint-artifact"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Flyway.class);
                    assertThat(context).getBean(Flyway.class).satisfies(flyway -> {
                        assertThat(flyway.info().current().getVersion().getMajorAsString())
                                .isEqualTo("1");
                        assertThat(flyway.info().current().getVersion().getMinorAsString())
                                .isEqualTo("3");
                    });
                });
    }

}