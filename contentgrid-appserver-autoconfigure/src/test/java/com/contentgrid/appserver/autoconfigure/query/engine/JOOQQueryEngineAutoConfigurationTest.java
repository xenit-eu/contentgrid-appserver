package com.contentgrid.appserver.autoconfigure.query.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQTimedCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class JOOQQueryEngineAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            // Use initializer to have default conversion service
            .withInitializer(applicationContext -> applicationContext.getBeanFactory().setConversionService(new ApplicationConversionService()))
            .withInitializer(ConditionEvaluationReportLoggingListener.forLogLevel(LogLevel.INFO))
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, TransactionAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, JooqAutoConfiguration.class,
                    JOOQQueryEngineAutoConfiguration.class));
    @Test
    void checkDefaults() {
        contextRunner
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DSLContextResolver.class);
                    assertThat(context).hasSingleBean(QueryEngine.class);
                    assertThat(context).doesNotHaveBean("tableInitializer");
                });
    }

    @Test
    void checkWithBootStrapTables() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.query-engine.bootstrap-tables=true")
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DSLContextResolver.class);
                    assertThat(context).hasSingleBean(QueryEngine.class);
                    assertThat(context).hasBean("tableInitializer");
                });
    }

    @Test
    void checkWithBootStrapTables_noApplication() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.query-engine.bootstrap-tables=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DSLContextResolver.class);
                    assertThat(context).hasSingleBean(QueryEngine.class);
                    assertThat(context).doesNotHaveBean("tableInitializer");
                });
    }

    @Test
    void checkTimedCountStrategy_timeoutSpecified() {
        contextRunner
                .withPropertyValues("contentgrid.appserver.query-engine.count.timeout=1s")
                .withUserConfiguration(TestConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JOOQTimedCountStrategy.class);
                    assertThat(context).getBean(JOOQTimedCountStrategy.class)
                            .hasFieldOrPropertyWithValue("timeout", Duration.ofSeconds(1));
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