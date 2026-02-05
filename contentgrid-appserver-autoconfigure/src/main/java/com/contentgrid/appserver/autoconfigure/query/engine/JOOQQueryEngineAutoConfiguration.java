package com.contentgrid.appserver.autoconfigure.query.engine;

import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.autoconfigure.json.schema.ApplicationResolverAutoConfiguration;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.JOOQTableCreator;
import com.contentgrid.appserver.query.engine.jooq.TransactionalQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQTimedCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.appserver.registry.ApplicationResolver;
import java.time.Duration;
import org.jooq.DSLContext;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jooq.ExceptionTranslatorExecuteListener;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(after = {ApplicationResolverAutoConfiguration.class}, before = JooqAutoConfiguration.class)
@ConditionalOnClass(JOOQQueryEngine.class)
public class JOOQQueryEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DSLContextResolver dslContextResolver(DSLContext dslContext) {
        return new AutowiredDSLContextResolver(dslContext);
    }

    @Bean
    JOOQCountStrategy jooqTimedCountStrategy(@Value("${contentgrid.appserver.query-engine.count.timeout:500ms}") Duration timeout) {
        return new JOOQTimedCountStrategy(timeout);
    }

    @Bean
    QueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver, JOOQCountStrategy countStrategy,
            PlatformTransactionManager transactionManager) {
        return new TransactionalQueryEngine(new JOOQQueryEngine(dslContextResolver, countStrategy), transactionManager);
    }

    @Bean
    ExceptionTranslatorExecuteListener jooqNoneExceptionTranslatorExecuteListener() {
        return new ExceptionTranslatorExecuteListener() {
            // No implementation, to fall back to jOOQ defaults and exceptions are not wrapped by spring
        };
    }

    @Bean
    TableCreator jooqTableCreator(DSLContextResolver dslContextResolver) {
        return new JOOQTableCreator(dslContextResolver);
    }

    @Bean
    @ConditionalOnBean(ApplicationResolver.class)
    TableInitializer jooqTableInitializer(
            TableCreator tableCreator,
            ApplicationResolver applicationResolver,
            @Value("${contentgrid.appserver.query-engine.bootstrap-tables:NONE}") Bootstrap bootstrap) {
        return new TableInitializer(tableCreator, applicationResolver, bootstrap);
    }

    @lombok.Value
    static class TableInitializer implements InitializingBean, DisposableBean {

        private static final ApplicationName APPLICATION_NAME = ApplicationName.of("default");

        TableCreator tableCreator;
        ApplicationResolver applicationResolver;
        Bootstrap bootstrap;

        @Override
        public void afterPropertiesSet() throws Exception {
            if (bootstrap == Bootstrap.CREATE || bootstrap == Bootstrap.CREATE_DROP) {
                tableCreator.createTables(applicationResolver.resolve(APPLICATION_NAME));
            }
        }

        @Override
        public void destroy() throws Exception {
            if (bootstrap == Bootstrap.CREATE_DROP) {
                tableCreator.dropTables(applicationResolver.resolve(APPLICATION_NAME));
            }
        }

    }

    enum Bootstrap {
        NONE,
        CREATE,
        CREATE_DROP
    }
}
