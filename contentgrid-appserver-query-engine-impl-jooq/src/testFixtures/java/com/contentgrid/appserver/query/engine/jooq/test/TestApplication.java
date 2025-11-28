package com.contentgrid.appserver.query.engine.jooq.test;

import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.JOOQTableCreator;
import com.contentgrid.appserver.query.engine.jooq.TransactionalQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQTimedCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import java.time.Duration;
import org.jooq.DSLContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jooq.ExceptionTranslatorExecuteListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public DSLContextResolver autowiredDSLContextResolver(DSLContext dslContext) {
        return new AutowiredDSLContextResolver(dslContext);
    }

    @Bean
    ExceptionTranslatorExecuteListener noopExceptionTranslator() {
        return new ExceptionTranslatorExecuteListener() {
        };
    }

    @Bean
    public TableCreator jooqTableCreator(DSLContextResolver dslContextResolver) {
        return new JOOQTableCreator(dslContextResolver);
    }

    @Bean
    public JOOQQueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver) {
        return new JOOQQueryEngine(dslContextResolver, new JOOQTimedCountStrategy(Duration.ofMillis(500)));
    }

    @Bean
    @Primary
    public QueryEngine queryEngine(
            JOOQQueryEngine jooqQueryEngine,
            PlatformTransactionManager transactionManager
    ) {
        return new TransactionalQueryEngine(
                jooqQueryEngine,
                transactionManager
        );
    }
}
