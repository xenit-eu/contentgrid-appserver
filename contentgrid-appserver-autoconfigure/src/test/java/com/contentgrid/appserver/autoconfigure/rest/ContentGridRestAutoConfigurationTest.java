package com.contentgrid.appserver.autoconfigure.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.autoconfigure.contentstore.FileSystemContentStoreAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.domain.ContentGridDomainAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.thunx.api.autoconfigure.AbacContextAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ContentGridRestAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(
                    // Autoconfigurations for database
                    DataSourceAutoConfiguration.class,
                    TransactionAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class,
                    JooqAutoConfiguration.class,
                    JOOQQueryEngineAutoConfiguration.class,
                    // autoconfiguration for content store
                    FileSystemContentStoreAutoConfiguration.class,
                    // autoconfiguration for domain
                    ContentGridDomainAutoConfiguration.class,
                    // autoconfigurations for rest
                    WebMvcAutoConfiguration.class,
                    AbacContextAutoConfiguration.class,
                    ContentGridRestAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("spring.datasource.url=jdbc:tc:postgresql:15:///");

    @Test
    void checkDefaults() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(EntityRestController.class);
                });
    }


    @Configuration
    static class TestConfiguration {

        @Bean
        ApplicationResolver testApplicationResolver() {
            return new SingleApplicationResolver(Application.builder()
                    .name(ApplicationName.of("default"))
                    .build());
        }
    }
}