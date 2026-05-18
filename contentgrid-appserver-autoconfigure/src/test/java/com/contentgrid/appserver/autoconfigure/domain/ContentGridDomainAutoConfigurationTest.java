package com.contentgrid.appserver.autoconfigure.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.autoconfigure.contentstore.FilesystemContentStoreAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.infrastructure.InfrastructureAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration;
import com.contentgrid.appserver.domain.automations.AutomationsModel;
import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationAnnotationModel;
import com.contentgrid.appserver.domain.automations.AutomationsModel.AutomationModel;
import com.contentgrid.appserver.domain.automations.AutomationsModelResolver;
import com.contentgrid.appserver.domain.automations.SingleAutomationsModelResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ContentGridDomainAutoConfigurationTest {

    private static final String AUTOMATION_ID = "a0da9322-3dae-4b5a-8f92-c3cb53989938";
    private static final String SYSTEM_ID = "my-system";
    private static final Map<String, Object> AUTOMATION_DATA = Map.of("foo", "bar");
    private static final String ENTITY_ANNOTATION_ID = "92aeb778-e2b8-42c1-8c3b-513d18ff01cb";
    private static final Map<String, String> ENTITY_ANNOTATION_SUBJECT = Map.of("type", "entity", "entity", "invoice");
    private static final Map<String, Object> ENTITY_ANNOTATION_DATA = Map.of("color", "blue");
    private static final String ATTRIBUTE_ANNOTATION_ID = "4fb6993d-a163-4746-a1aa-4a0018a796d4";
    private static final Map<String, String> ATTRIBUTE_ANNOTATION_SUBJECT = Map.of("type", "attribute", "entity", "invoice", "attribute", "content");
    private static final Map<String, Object> ATTRIBUTE_ANNOTATION_DATA = Map.of("type", "input");

    private static final AutomationsModel MODEL = AutomationsModel.builder()
            .automations(List.of(
                    AutomationModel.builder()
                            .id(AUTOMATION_ID)
                            .system(SYSTEM_ID)
                            .name("my-automation")
                            .data(AUTOMATION_DATA)
                            .annotations(List.of(
                                    AutomationAnnotationModel.builder()
                                            .id(ENTITY_ANNOTATION_ID)
                                            .subject(ENTITY_ANNOTATION_SUBJECT)
                                            .data(ENTITY_ANNOTATION_DATA)
                                            .build(),
                                    AutomationAnnotationModel.builder()
                                            .id(ATTRIBUTE_ANNOTATION_ID)
                                            .subject(ATTRIBUTE_ANNOTATION_SUBJECT)
                                            .data(ATTRIBUTE_ANNOTATION_DATA)
                                            .build()
                            ))
                            .build()
            ))
            .build();

    // dummy app for resolving AutomationsModel
    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("default"))
            .build();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
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
                    FilesystemContentStoreAutoConfiguration.class,
                    // autoconfiguration for infrastructure
                    InfrastructureAutoConfiguration.class,
                    // autoconfiguration for domain
                    ContentGridDomainAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:tc:postgresql:15:///",
                    "contentgrid.appserver.content-store.type=ephemeral",
                    "contentgrid.events.rabbitmq.enabled=false"
            );

    @Test
    void checkDefaults() {
        contextRunner
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AutomationsModelResolver.class);
                    assertThat(context).hasBean("defaultAutomationsResolver");
                    assertThat(context).getBean(AutomationsModelResolver.class).satisfies(resolver ->
                            assertThat(resolver.resolve(APPLICATION)).isEqualTo(MODEL));
                });
    }

    @Test
    void checkWithCustomAutomationsModelResolver() {
        contextRunner
                .withUserConfiguration(ContentGridDomainAutoConfigurationTest.TestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AutomationsModelResolver.class);
                    assertThat(context).doesNotHaveBean("defaultAutomationsResolver");
                });
    }

    @Test
    void checkWithMissingAutomationsFile() {
        contextRunner
                // there is no /automation/automation/automations.json
                .withPropertyValues("contentgrid.appserver.infrastructure.location=classpath:automation")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AutomationsModelResolver.class);
                    assertThat(context).getBean(AutomationsModelResolver.class).satisfies(resolver ->
                            assertThat(resolver.resolve(APPLICATION).getAutomations()).isEmpty());
                });
    }


    @Configuration
    static class TestConfiguration {

        @Bean
        AutomationsModelResolver testAutomationsModelResolver() {
            return new SingleAutomationsModelResolver(AutomationsModel.builder()
                    .automations(List.of(AutomationModel.builder()
                            .id(AUTOMATION_ID)
                            .system(SYSTEM_ID)
                            .name("test-automation")
                            .data(Map.of("type", "test"))
                            .annotations(List.of())
                            .build()
                    ))
                    .build());
        }
    }
}