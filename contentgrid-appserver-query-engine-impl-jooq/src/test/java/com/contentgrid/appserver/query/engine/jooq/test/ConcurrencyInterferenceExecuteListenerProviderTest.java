package com.contentgrid.appserver.query.engine.jooq.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.query.engine.jooq.TransactionalQueryEngine;
import com.contentgrid.appserver.query.engine.jooq.test.ConcurrencyInterferenceExecuteListenerProviderTest.Config;
import com.contentgrid.appserver.query.engine.jooq.test.concurrency.ConcurrencyInterferenceExecuteListenerProvider;
import com.contentgrid.appserver.query.engine.jooq.test.concurrency.UnderTestRunnable;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

@JooqTest
@ContextConfiguration(classes = {TestApplication.class, Config.class})
class ConcurrencyInterferenceExecuteListenerProviderTest {
    @Autowired
    ConcurrencyInterferenceExecuteListenerProvider tester;

    @Autowired
    DSLContext dslContext;

    private static final Table<?> TEST = DSL.table(DSL.name("test"));
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
    private static final Field<String> NAME = DSL.field(DSL.name("name"), SQLDataType.VARCHAR);

    @BeforeEach
    void setup() {
        dslContext.createTable(TEST)
                .column(ID)
                .column(NAME)
                .primaryKey(ID)
                .execute();
    }

    @AfterEach
    void teardown() {
        dslContext.dropTable("test").execute();
    }

    @Test
    void detectsAndRunsAllQueries() {
        AtomicInteger mainExecutionCount = new AtomicInteger();
        AtomicInteger interferenceExecutionCount = new AtomicInteger();
        tester.runConcurrencyTest(UnderTestRunnable.test(() -> {
                            return insert("A");
                        }, (createdId) -> {
                            mainExecutionCount.incrementAndGet();
                            var result = dslContext.select(DSL.asterisk()).from(TEST)
                                    .where(NAME.eq("A"))
                                    .fetch();
                            if (!result.isEmpty()) {
                                return insert("B");
                            } else {
                                return result;
                            }
                        })
                        .cleanup(() -> {
                            dslContext.truncate(TEST).execute();
                        })
                , () -> {
                    interferenceExecutionCount.incrementAndGet();
                    dslContext.deleteFrom(TEST).where(NAME.eq("A")).execute();
                });

        // Run once during pre-run, counting 2 queries
        // and then 2 times during interference
        assertThat(mainExecutionCount.get()).isEqualTo(3);

        // interference should also have been run each time
        assertThat(interferenceExecutionCount.get()).isEqualTo(3);
    }

    @Test
    void interferenceAffectsVerification() {
        tester.runConcurrencyTest(UnderTestRunnable.test(() -> {
                            return insert("A").get(ID);
                        }, (createdId) -> {
                            return insert(createdId.toString());
                        })
                        .verify((createdId, testResult) -> {
                            // The object created during setup is always deleted
                            assertThat(dslContext.selectCount().from(TEST).where(ID.eq(createdId)).fetchSingle().component1()).isEqualTo(0);
                            // But the object created during test is not
                            assertThat(dslContext.selectCount().from(TEST).where(NAME.eq(createdId.toString())).fetchSingle().component1()).isEqualTo(1);
                        })
                        .cleanup(() -> {
                            dslContext.truncate(TEST).execute();
                        }),
                (createdId) -> {
                    dslContext.deleteFrom(TEST).where(ID.eq(createdId)).execute();

                }
        );
    }


    private Record insert(String name) {
        return dslContext.insertInto(TEST, ID, NAME)
                .values(UUID.randomUUID(), name)
                .returning(ID)
                .fetchSingle();
    }


    @TestConfiguration
    static class Config {

        @Bean
        ConcurrencyInterferenceExecuteListenerProvider concurrencyInterferenceExecuteListenerProvider() {
            return new ConcurrencyInterferenceExecuteListenerProvider();
        }

        /**
         * Disable retries on the query engine.
         * Retries mess with the concurrency tests, and they just run the same operations again at a later time
         * when the conflicting transaction hopefully has finished already.
         * They are subject to the same concurrency race again, so we don't have to retest them separately
         */
        @Bean
        static BeanPostProcessor postProcessDisableQueryEngineRetries() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if(bean instanceof TransactionalQueryEngine transactionalQueryEngine) {
                        transactionalQueryEngine.setMaxRetries(0);
                    }
                    return bean;
                }
            };
        }
    }
}