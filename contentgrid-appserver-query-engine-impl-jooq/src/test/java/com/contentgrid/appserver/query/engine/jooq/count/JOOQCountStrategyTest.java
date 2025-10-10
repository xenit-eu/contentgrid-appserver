package com.contentgrid.appserver.query.engine.jooq.count;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.JOOQTableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Select;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

@SpringBootTest(properties = {
        "logging.level.org.jooq.tools.LoggerListener=DEBUG"
})
class JOOQCountStrategyTest {

    private static final SimpleAttribute PRODUCT_CODE = SimpleAttribute.builder()
            .name(AttributeName.of("code"))
            .column(ColumnName.of("code"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .constraint(Constraint.unique())
            .build();

    private static final SimpleAttribute PRODUCT_DESCRIPTION = SimpleAttribute.builder()
            .name(AttributeName.of("description"))
            .column(ColumnName.of("description"))
            .type(Type.TEXT)
            .build();


    private static final Entity PRODUCT = Entity.builder()
            .name(EntityName.of("product"))
            .table(TableName.of("product"))
            .pathSegment(PathSegmentName.of("products"))
            .linkName(LinkName.of("products"))
            .attribute(PRODUCT_CODE)
            .attribute(PRODUCT_DESCRIPTION)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("code"))
                    .attribute(PRODUCT_CODE)
                    .build())
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("invoicing-application"))
            .entity(PRODUCT)
            .build();

    private static final EntityId PRODUCT1_ID = EntityId.of(UUID.randomUUID());
    private static final EntityId PRODUCT2_ID = EntityId.of(UUID.randomUUID());
    private static final EntityId PRODUCT3_ID = EntityId.of(UUID.randomUUID());

    private static final Select<?> ALLOW_ALL = DSL.selectFrom(DSL.table("product").where(DSL.condition(true)));
    private static final Select<?> DENY_ALL = DSL.selectFrom(DSL.table("product").where(DSL.condition(false)));

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private TableCreator tableCreator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
        dslContext.insertInto(DSL.table("product"),
                        DSL.field("id", UUID.class), DSL.field("code", String.class), DSL.field("description", String.class))
                .values(PRODUCT1_ID.getValue(), "code_1", "test description")
                .values(PRODUCT2_ID.getValue(), "code_2", "")
                .values(PRODUCT3_ID.getValue(), "code_3", null)
                .execute();
    }

    @AfterEach
    void cleanup() {
        tableCreator.dropTables(APPLICATION);
    }

    @Test
    void testExplainEstimateCount() {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            var countStrategy = new JOOQExplainEstimateCountStrategy();
            var count = countStrategy.count(dslContext, ALLOW_ALL).count();

            assertTrue(count >= 1);
            assertTrue(count <= 10_000);

            count = countStrategy.count(dslContext, DENY_ALL).count();

            assertEquals(0, count);
        } finally {
            transactionManager.commit(transaction);
        }
    }

    static Stream<JOOQCountStrategy> testExactCount() {
        return Stream.of(new JOOQExactCountStrategy(), new JOOQTimedCountStrategy(Duration.ofMillis(500)));
    }

    @ParameterizedTest
    @MethodSource
    void testExactCount(JOOQCountStrategy countStrategy) {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            var count = countStrategy.count(dslContext, ALLOW_ALL).count();
            assertEquals(3, count);

            count = countStrategy.count(dslContext, DENY_ALL).count();
            assertEquals(0, count);
        } finally {
            transactionManager.commit(transaction);
        }
    }

    @Test
    void testTimedCount_outOfTime() {
        var transaction = transactionManager.getTransaction(TransactionDefinition.withDefaults());
        try {
            // Setup of intercept schema
            dslContext.createSchema(DSL.name("intercept")).execute();
            dslContext.createView(DSL.name("intercept", "product"))
                    .as(DSL.sql("""
                        SELECT product.*
                        FROM public.product
                        CROSS JOIN LATERAL pg_sleep(2);
                        """)).execute();
            dslContext.execute(DSL.sql("SET LOCAL search_path = intercept, public;"));

            // Perform test
            var countStrategy = new JOOQTimedCountStrategy(Duration.ofMillis(500));

            var count = countStrategy.count(dslContext, ALLOW_ALL);

            assertTrue(count.isEstimated());
        } finally {
            transactionManager.rollback(transaction);
        }
    }

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }

        @Bean
        public DSLContextResolver autowiredDSLContextResolver(DSLContext dslContext) {
            return new AutowiredDSLContextResolver(dslContext);
        }

        @Bean
        public TableCreator jooqTableCreator(DSLContextResolver dslContextResolver) {
            return new JOOQTableCreator(dslContextResolver);
        }
    }
}