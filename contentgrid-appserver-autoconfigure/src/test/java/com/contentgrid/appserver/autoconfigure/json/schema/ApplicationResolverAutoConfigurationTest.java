package com.contentgrid.appserver.autoconfigure.json.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ApplicationResolverAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(ApplicationResolverAutoConfiguration.class));

    @Test
    void checkWithoutProperty() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ApplicationResolver.class);
                });
    }

    @Test
    void checkWithProperty() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.application-model=test.json")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SingleApplicationResolver.class);
                    assertThat(context).getBean(SingleApplicationResolver.class)
                            .returns(false, resolver -> resolver.getApplication().getEntities().isEmpty());
                });
    }

    @Test
    void checkWithProperty_unknownValue() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.application-model=unknown.json")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithPropertyAndApplicationResolver() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.application-model=test.json")
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SingleApplicationResolver.class);
                    assertThat(context).getBean(SingleApplicationResolver.class)
                            .returns(true, resolver -> resolver.getApplication().getEntities().isEmpty());
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