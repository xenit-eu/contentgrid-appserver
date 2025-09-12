package com.contentgrid.appserver.autoconfigure.query.engine;

import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.autoconfigure.json.schema.ApplicationResolverAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.query.engine.JOOQQueryEngineAutoConfiguration.TableBootstrapConfiguration;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.transaction.PlatformTransactionManager;

@AutoConfiguration(after = {JooqAutoConfiguration.class, ApplicationResolverAutoConfiguration.class})
@ConditionalOnClass(JOOQQueryEngine.class)
@ConditionalOnBean({DSLContext.class, PlatformTransactionManager.class})
@Import(TableBootstrapConfiguration.class)
public class JOOQQueryEngineAutoConfiguration {

    @Bean
    DSLContextResolver dslContextResolver(DSLContext dslContext) {
        return new AutowiredDSLContextResolver(dslContext);
    }

    @Bean
    JOOQCountStrategy jooqTimedCountStrategy(@Value("${contentgrid.appserver.query-engine.count.timeout:500ms}") Duration timeout) {
        return new JOOQTimedCountStrategy(timeout);
    }

    @Bean
    QueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver, JOOQCountStrategy countStrategy, PlatformTransactionManager transactionManager) {
        return new TransactionalQueryEngine(new JOOQQueryEngine(dslContextResolver, countStrategy), transactionManager);
    }

    @Bean
    TableCreator jooqTableCreator(DSLContextResolver dslContextResolver) {
        return new JOOQTableCreator(dslContextResolver);
    }

    @Configuration
    @Conditional(NotTest.class)
    @ConditionalOnBean(ApplicationResolver.class)
    @ConditionalOnBooleanProperty("contentgrid.appserver.query-engine.bootstrap-tables")
    static class TableBootstrapConfiguration {

        @Bean
        InitializingBean bootstrapTables(TableCreator tableCreator, ApplicationResolver applicationResolver) {
            return () -> tableCreator.createTables(applicationResolver.resolve(ApplicationName.of("default")));
        }

        @Bean
        DisposableBean destroyTables(TableCreator tableCreator, ApplicationResolver applicationResolver) {
            return () -> tableCreator.dropTables(applicationResolver.resolve(ApplicationName.of("default")));
        }
    }


    private static class NotTest implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if(context.getEnvironment() instanceof ConfigurableEnvironment configurableEnvironment) {
                return !configurableEnvironment.getPropertySources().contains("test");
            }
            return true;
        }
    }
}
