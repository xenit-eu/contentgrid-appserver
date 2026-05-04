package com.contentgrid.appserver.autoconfigure.json.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.autoconfigure.infrastructure.InfrastructureAutoConfiguration;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.ApplicationResolverRegistry;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ApplicationResolverAutoConfigurationTest {

    private static final ApplicationName APPLICATION = ApplicationName.of("default");

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(
                    ApplicationResolverAutoConfiguration.class,
                    InfrastructureAutoConfiguration.class
            ));

    @Test
    void checkWithoutProperties() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApplicationResolverRegistry.class);
                    assertThat(context).getBean(ApplicationResolverRegistry.class)
                            .returns(false, resolver ->
                                    resolver.resolve(APPLICATION).orElseThrow().getEntities().isEmpty());
                });
    }

    @Test
    void checkWithInfrastructureLocationProperty() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.infrastructure.location=classpath:.")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApplicationResolverRegistry.class);
                    assertThat(context).getBean(ApplicationResolverRegistry.class)
                            .returns(false, resolver ->
                                    resolver.resolve(APPLICATION).orElseThrow().getEntities().isEmpty());
                });
    }

    @Test
    void checkWithApplicationModelProperty() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.application-model=classpath:application-model.json")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApplicationResolverRegistry.class);
                    assertThat(context).getBean(ApplicationResolverRegistry.class)
                            .returns(false, resolver ->
                                    resolver.resolve(APPLICATION).orElseThrow().getEntities().isEmpty());
                });
    }

    @Test
    void checkWithApplicationModelProperty_nonExistingValue() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.application-model=classpath:unknown.json")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithInfrastructureLocationProperty_nonExistingValue() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.infrastructure.location=classpath:unknown")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void checkWithApplicationModelPropertyAndApplicationResolver() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.application-model=classpath:application-model.json")
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApplicationResolverRegistry.class);
                    assertThat(context).getBean(ApplicationResolverRegistry.class)
                            .returns(true, resolver ->
                                    resolver.resolve(APPLICATION).orElseThrow().getEntities().isEmpty());
                });
    }

    @Test
    void checkWithInfrastructureLocationPropertyAndApplicationResolver() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.infrastructure.location=classpath:.")
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApplicationResolverRegistry.class);
                    assertThat(context).getBean(ApplicationResolverRegistry.class)
                            .returns(true, resolver ->
                                    resolver.resolve(APPLICATION).orElseThrow().getEntities().isEmpty());
                });
    }

    @Configuration
    static class TestConfiguration {

        @Bean
        ApplicationResolver testApplicationResolver() {
            return name -> Optional.of(Application.builder().name(name).build());
        }
    }
}