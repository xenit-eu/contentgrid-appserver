package com.contentgrid.appserver.query.engine.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.query.engine.api.EntityIdAndVersion;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.ConcurrencyFailureException;
import com.contentgrid.appserver.query.engine.api.exception.RelationLinkNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import com.contentgrid.appserver.query.engine.jooq.ConcurrencyJOOQQueryEngineTest.Config;
import com.contentgrid.appserver.query.engine.jooq.test.JooqTest;
import com.contentgrid.appserver.query.engine.jooq.test.NoneEvents;
import com.contentgrid.appserver.query.engine.jooq.test.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.test.concurrency.ConcurrencyInterferenceExecuteListenerProvider;
import com.contentgrid.appserver.query.engine.jooq.test.concurrency.UnderTestRunnable;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

@JooqTest
@ContextConfiguration(classes = {TestApplication.class, Config.class})
@Slf4j
class ConcurrencyJOOQQueryEngineTest {
    @Autowired
    QueryEngine queryEngine;

    @Autowired
    ConcurrencyInterferenceExecuteListenerProvider tester;

    @Autowired
    JOOQTableCreator tableCreator;

    @Autowired
    DSLContext dslContext;

    private static final ThunkExpression<Boolean> PERMIT_ALWAYS = Scalar.of(true);

    private static final NoneEvents NONE_EVENTS = new NoneEvents();


    @AfterEach
    void cleanup() {
        dslContext.dropSchema("public").cascade().execute();
        dslContext.createSchema("public").execute();
    }

    private static final SimpleAttribute VERSION_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("_version"))
            .column(ColumnName.of("_version"))
            .type(Type.LONG)
            .flag(ETagFlag.INSTANCE)
            .build();

    private static final Entity ENTITY_A = Entity.builder()
            .name(EntityName.of("entityA"))
            .pathSegment(PathSegmentName.of("entity-a"))
            .linkName(LinkName.of("entity-a"))
            .table(TableName.of("entity_a"))
            .attribute(VERSION_ATTR)
            .build();

    private static final Entity ENTITY_B = Entity.builder()
            .name(EntityName.of("entityB"))
            .pathSegment(PathSegmentName.of("entity-b"))
            .linkName(LinkName.of("entity-b"))
            .table(TableName.of("entity_b"))
            .attribute(VERSION_ATTR)
            .build();

    static Stream<Arguments> toOneRelations() {
        var sourceEndpoint = RelationEndPoint.builder()
                .entity(ENTITY_A.getName())
                .name(RelationName.of("to_b"))
                .pathSegment(PathSegmentName.of("to-b"))
                .linkName(LinkName.of("to-b"))
                .build();
        var targetEndpoint = RelationEndPoint.builder()
                .entity(ENTITY_B.getName())
                .name(RelationName.of("to_a"))
                .flag(HiddenEndpointFlag.INSTANCE)
                .build();
        return Stream.of(
                Arguments.argumentSet("source one-to-one", SourceOneToOneRelation.builder()
                        .sourceEndPoint(sourceEndpoint)
                        .targetEndPoint(targetEndpoint)
                        .targetReference(ColumnName.of("entity_b_id"))
                        .build()),
                Arguments.argumentSet("target one-to-one", TargetOneToOneRelation.builder()
                        .sourceEndPoint(sourceEndpoint)
                        .targetEndPoint(targetEndpoint)
                        .sourceReference(ColumnName.of("entity_a_id"))
                        .build()),
                Arguments.argumentSet("many-to-one", ManyToOneRelation.builder()
                        .sourceEndPoint(sourceEndpoint)
                        .targetEndPoint(targetEndpoint)
                        .targetReference(ColumnName.of("entity_b_id"))
                        .build())
        );
    }

    static Stream<Arguments> toManyRelations() {
        var sourceEndpoint = RelationEndPoint.builder()
                .entity(ENTITY_A.getName())
                .name(RelationName.of("to_bs"))
                .pathSegment(PathSegmentName.of("to-bs"))
                .linkName(LinkName.of("to-bs"))
                .build();
        var targetEndpoint = RelationEndPoint.builder()
                .entity(ENTITY_B.getName())
                .name(RelationName.of("to_a"))
                .flag(HiddenEndpointFlag.INSTANCE)
                .build();

        return Stream.of(
                Arguments.argumentSet("one-to-many", OneToManyRelation.builder()
                        .sourceEndPoint(sourceEndpoint)
                        .targetEndPoint(targetEndpoint)
                        .sourceReference(ColumnName.of("entity_a_id"))
                        .build()),
                Arguments.argumentSet("many-to-many", ManyToManyRelation.builder()
                        .sourceEndPoint(sourceEndpoint)
                        .targetEndPoint(targetEndpoint)
                        .joinTable(TableName.of("join_a_b"))
                        .sourceReference(ColumnName.of("entity_a_id"))
                        .targetReference(ColumnName.of("entity_b_id"))
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void setEmptyToOneRelation(Relation relation) {
        var app = createModel(relation);

        var entityA = createItem(app, ENTITY_A.getName());
        var entityB1 = createItem(app, ENTITY_B.getName());
        var entityB2 = createItem(app, ENTITY_B.getName());

        var relReq = RelationRequest.forRelation(entityA.getIdentity(), relation.getSourceEndPoint().getName());

        tester.runConcurrencyTest(
                UnderTestRunnable.test(() -> assertThatCode(() -> queryEngine.setLink(app, relReq.withVersionConstraint(Version.nonExisting()), entityB1.getId(), PERMIT_ALWAYS, NONE_EVENTS)))
                        .verify(thrown -> thrown.doesNotThrowAnyExceptionExcept(UnsatisfiedVersionException.class, ConcurrencyFailureException.class))
                        .verify(thrown -> {
                            var maybeTarget = queryEngine.findTarget(app, relReq, PERMIT_ALWAYS);
                            thrown.satisfiesAnyOf(
                                    throwable -> {
                                        assertThat(throwable).isNull();
                                        assertThat(maybeTarget).map(EntityIdAndVersion::entityId).hasValue(entityB1.getId());
                                    },
                                    throwable -> {
                                        assertThat(throwable).isOfAnyClassIn(UnsatisfiedVersionException.class, ConcurrencyFailureException.class);
                                        assertThat(maybeTarget).map(EntityIdAndVersion::entityId).hasValue(entityB2.getId());
                                    }
                            );
                        })
                        .cleanup(() -> {
                            queryEngine.unsetLink(app, relReq, PERMIT_ALWAYS, NONE_EVENTS);
                        })
                ,
                () -> assertThatCode(() -> queryEngine.setLink(app, relReq, entityB2.getId(), PERMIT_ALWAYS, NONE_EVENTS))
                        // This piece of code that runs concurrently might sometimes cause an unsatisfied version error as well,
                        // when the test update has run before this one
                        .doesNotThrowAnyExceptionExcept(UnsatisfiedVersionException.class, ConcurrencyFailureException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void setFilledToOneRelation(Relation relation) {
        var app = createModel(relation);

        var entityA = createItem(app, ENTITY_A.getName());
        var originalValue = createItem(app, ENTITY_B.getName());
        var entityB1 = createItem(app, ENTITY_B.getName());
        var entityB2 = createItem(app, ENTITY_B.getName());

        var relReq = RelationRequest.forRelation(entityA.getIdentity(), relation.getSourceEndPoint().getName());

        tester.runConcurrencyTest(
                UnderTestRunnable.test(() -> {
                    queryEngine.setLink(app, relReq, originalValue.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                    return queryEngine.findTarget(app, relReq, PERMIT_ALWAYS)
                            .map(EntityIdAndVersion::version)
                            .orElseThrow();
                }, version -> assertThatCode(() -> queryEngine.setLink(app, relReq.withVersionConstraint(version), entityB1.getId(), PERMIT_ALWAYS, NONE_EVENTS)))
                        .verify(thrown -> thrown.doesNotThrowAnyExceptionExcept(UnsatisfiedVersionException.class))
                        .verify(thrown -> {
                            var target = queryEngine.findTarget(app, relReq, PERMIT_ALWAYS).orElseThrow();
                            thrown.satisfiesAnyOf(
                                    throwable -> {
                                        assertThat(throwable).isNull();
                                        assertThat(target.entityId()).isEqualTo(entityB1.getId());
                                    },
                                    throwable -> {
                                        assertThat(throwable).isInstanceOf(UnsatisfiedVersionException.class);
                                        assertThat(target.entityId()).isEqualTo(entityB2.getId());
                                    }
                            );
                        })
                ,
                () -> assertThatCode(() -> queryEngine.setLink(app, relReq, entityB2.getId(), PERMIT_ALWAYS, NONE_EVENTS))
                        .doesNotThrowAnyExceptionExcept(UnsatisfiedVersionException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void clearFilledToOneRelation(Relation relation) {
        var app = createModel(relation);

        var entityA = createItem(app, ENTITY_A.getName());
        var originalValue = createItem(app, ENTITY_B.getName());
        var entityB2 = createItem(app, ENTITY_B.getName());

        var relReq = RelationRequest.forRelation(entityA.getIdentity(), relation.getSourceEndPoint().getName());

        tester.runConcurrencyTest(
                UnderTestRunnable.test(() -> {
                            queryEngine.setLink(app, relReq, originalValue.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                            return queryEngine.findTarget(app, relReq, PERMIT_ALWAYS)
                                    .map(EntityIdAndVersion::version)
                                    .orElseThrow();
                        }, version -> assertThatCode(() -> queryEngine.unsetLink(app, relReq.withVersionConstraint(version), PERMIT_ALWAYS, NONE_EVENTS)))
                        .verify(thrown -> thrown.doesNotThrowAnyExceptionExcept(UnsatisfiedVersionException.class))
                        .verify(thrown -> {
                            var maybeTarget = queryEngine.findTarget(app, relReq, PERMIT_ALWAYS);
                            thrown.satisfiesAnyOf(
                                    throwable -> {
                                        assertThat(throwable).isNull();
                                        assertThat(maybeTarget).isEmpty();
                                    },
                                    throwable -> {
                                        assertThat(throwable).isInstanceOf(UnsatisfiedVersionException.class);
                                        assertThat(maybeTarget).map(EntityIdAndVersion::entityId).hasValue(entityB2.getId());
                                    }
                            );
                        })
                ,
                () -> assertThatCode(() -> queryEngine.setLink(app, relReq, entityB2.getId(), PERMIT_ALWAYS, NONE_EVENTS))
                        .doesNotThrowAnyExceptionExcept(UnsatisfiedVersionException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void concurrentlyClearFilledToOneRelation(Relation relation) {
        var app = createModel(relation);

        var entityA = createItem(app, ENTITY_A.getName());
        var originalValue = createItem(app, ENTITY_B.getName());

        var relReq = RelationRequest.forRelation(entityA.getIdentity(), relation.getSourceEndPoint().getName());

        tester.runConcurrencyTest(
                UnderTestRunnable.test(() -> {
                            queryEngine.setLink(app, relReq, originalValue.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                            return queryEngine.findTarget(app, relReq, PERMIT_ALWAYS)
                                    .map(EntityIdAndVersion::version)
                                    .orElseThrow();
                        }, version -> assertThatCode(() -> queryEngine.unsetLink(app, relReq.withVersionConstraint(version), PERMIT_ALWAYS, NONE_EVENTS)))
                        .verify(thrown -> thrown.doesNotThrowAnyExceptionExcept(UnsatisfiedVersionException.class))
                        .verify(thrown -> {
                            var maybeTarget = queryEngine.findTarget(app, relReq, PERMIT_ALWAYS);
                            assertThat(maybeTarget).isEmpty();
                            thrown.satisfiesAnyOf(
                                    throwable -> {
                                        assertThat(throwable).isNull();
                                    },
                                    throwable -> {
                                        assertThat(throwable).isInstanceOfSatisfying(UnsatisfiedVersionException.class, ex -> {
                                            assertThat(ex.getActualVersion()).isEqualTo(Version.nonExisting());
                                        });
                                    }
                            );
                        })
                ,
                () -> assertThatCode(() -> queryEngine.unsetLink(app, relReq, PERMIT_ALWAYS, NONE_EVENTS))
                        .satisfiesAnyOf(
                                throwable -> {
                                    assertThat(throwable).isNull();
                                },
                                throwable -> {
                                    assertThat(throwable).isInstanceOfSatisfying(UnsatisfiedVersionException.class, ex -> {
                                        assertThat(ex.getActualVersion()).isEqualTo(Version.nonExisting());
                                    });
                                }
                        )
        );
    }

    @ParameterizedTest
    @MethodSource("toManyRelations")
    void addItemsToManyRelation(Relation relation) {
        var app = createModel(relation);

        var entityA = createItem(app, ENTITY_A.getName());
        var entityB1 = createItem(app, ENTITY_B.getName());
        var entityB2 = createItem(app, ENTITY_B.getName());

        var relReq = RelationRequest.forRelation(entityA.getIdentity(), relation.getSourceEndPoint().getName());

        tester.runConcurrencyTest(
                UnderTestRunnable.test(() -> assertThatCode(() -> queryEngine.addLinks(app, relReq, Set.of(entityB1.getId()), PERMIT_ALWAYS, NONE_EVENTS)))
                        .verify(thrown -> thrown.doesNotThrowAnyException())
                        .verify(thrown -> {
                            assertThat(queryEngine.isLinked(app, relReq, entityB1.getId(), PERMIT_ALWAYS)).isTrue();
                        })
                        .cleanup(() -> queryEngine.unsetLink(app, relReq, PERMIT_ALWAYS, NONE_EVENTS))
                ,
                () -> queryEngine.addLinks(app, relReq, Set.of(entityB2.getId()), PERMIT_ALWAYS, NONE_EVENTS)
        );
    }

    @ParameterizedTest
    @MethodSource("toManyRelations")
    void concurrentlyAddSameItemToManyRelation(Relation relation) {
        var app = createModel(relation);

        var entityA = createItem(app, ENTITY_A.getName());
        var entityB1 = createItem(app, ENTITY_B.getName());

        var relReq = RelationRequest.forRelation(entityA.getIdentity(), relation.getSourceEndPoint().getName());

        tester.runConcurrencyTest(
                UnderTestRunnable.test(() -> assertThatCode(() -> queryEngine.addLinks(app, relReq, Set.of(entityB1.getId()), PERMIT_ALWAYS, NONE_EVENTS)))
                        .verify(thrown -> thrown.doesNotThrowAnyException())
                        .verify(thrown -> {
                            assertThat(queryEngine.isLinked(app, relReq, entityB1.getId(), PERMIT_ALWAYS)).isTrue();
                        })
                        .cleanup(() -> queryEngine.unsetLink(app, relReq, PERMIT_ALWAYS, NONE_EVENTS))
                ,
                () -> queryEngine.addLinks(app, relReq, Set.of(entityB1.getId()), PERMIT_ALWAYS, NONE_EVENTS)
        );
    }

    @ParameterizedTest
    @MethodSource("toManyRelations")
    void concurrentlyRemoveSameItemToManyRelation(Relation relation) {
        var app = createModel(relation);

        var entityA = createItem(app, ENTITY_A.getName());
        var entityB1 = createItem(app, ENTITY_B.getName());

        var relReq = RelationRequest.forRelation(entityA.getIdentity(), relation.getSourceEndPoint().getName());

        tester.runConcurrencyTest(UnderTestRunnable.test(
                                () -> queryEngine.addLinks(app, relReq, Set.of(entityB1.getId()), PERMIT_ALWAYS, NONE_EVENTS),
                                () -> assertThatCode(() -> queryEngine.removeLinks(app, relReq, Set.of(entityB1.getId()), PERMIT_ALWAYS, NONE_EVENTS))
                        )
                        // TODO: should this be allowed to throw RelationLinkNotFoundException, or should we consider "was already gone" as request fulfilled
                        .verify(thrown -> thrown.doesNotThrowAnyExceptionExcept(RelationLinkNotFoundException.class))
                        .verify(thrown -> {
                            assertThat(queryEngine.isLinked(app, relReq, entityB1.getId(), PERMIT_ALWAYS)).isFalse();
                        }),
                () -> assertThatCode(() ->queryEngine.removeLinks(app, relReq, Set.of(entityB1.getId()), PERMIT_ALWAYS, NONE_EVENTS))
                        .doesNotThrowAnyExceptionExcept(RelationLinkNotFoundException.class)
        );
    }

    private Application createModel(Relation relation) {
        var app = Application.builder()
                .name(ApplicationName.of("test"))
                .entity(ENTITY_A)
                .entity(ENTITY_B)
                .relation(relation)
                .build();
        tableCreator.createTables(app);
        return app;
    }

    private EntityData createItem(Application application, EntityName entityName) {
        var entity = application.getRequiredEntityByName(entityName);
        var relations = application.getRelationsForSourceEntity(entity);

        var entityDataBuilder = EntityCreateData.builder()
                .entityName(entityName);

        for (var relation : relations) {
            if (relation.getSourceEndPoint().isRequired()) {
                var targetItem = createItem(application, relation.getTargetEndPoint().getEntity());
                // Note: required endpoint can only be active when the other side is singular (not many)
                entityDataBuilder.relation(XToOneRelationData.builder()
                        .name(relation.getSourceEndPoint().getName())
                        .ref(targetItem.getId())
                        .build()
                );
            }
        }

        return queryEngine.create(application, entityDataBuilder.build(), PERMIT_ALWAYS, NONE_EVENTS);
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
