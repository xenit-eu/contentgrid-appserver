package com.contentgrid.appserver.query.engine.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint.RelationEndPointBuilder;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.RelationPath;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationIdentity;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.domain.values.version.ExactlyVersion;
import com.contentgrid.appserver.domain.values.version.NonExistingVersion;
import com.contentgrid.appserver.domain.values.version.UnspecifiedVersion;
import com.contentgrid.appserver.domain.values.version.Version;
import com.contentgrid.appserver.query.engine.api.CreateEventConsumer;
import com.contentgrid.appserver.query.engine.api.DeleteEventConsumer;
import com.contentgrid.appserver.query.engine.api.EntityIdAndVersion;
import com.contentgrid.appserver.query.engine.api.LinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.UnlinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.UpdateEventConsumer;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.api.exception.EntityIdNotFoundException;
import com.contentgrid.appserver.query.engine.api.exception.EntityLinkedByRequiredRelationException;
import com.contentgrid.appserver.query.engine.api.exception.RequiredConstraintViolationException;
import com.contentgrid.appserver.query.engine.api.exception.UnsatisfiedVersionException;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngineTest.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.StructuredRelationsJOOQQueryEngineTest.RelationArgumentFactory.UnbuildableException;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQTimedCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.google.common.collect.Sets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jooq.ExceptionTranslatorExecuteListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///",
        "logging.level.org.jooq.tools.LoggerListener=DEBUG"
})
@ContextConfiguration(classes = {TestApplication.class})
class StructuredRelationsJOOQQueryEngineTest {

    private static final ThunkExpression<Boolean> PERMIT_ALWAYS = Scalar.of(true);

    private static final NoneEvents NONE_EVENTS = new NoneEvents();

    @Autowired
    private QueryEngine queryEngine;

    @Autowired
    private JOOQTableCreator tableCreator;

    @Autowired
    private DSLContext dslContext;


    @AfterEach
    void cleanup() {
        dslContext.dropSchema("public").cascade().execute();
        dslContext.createSchema("public").execute();
    }

    private static final Entity ENTITY_A = Entity.builder()
            .name(EntityName.of("entityA"))
            .pathSegment(PathSegmentName.of("entity-a"))
            .linkName(LinkName.of("entity-a"))
            .table(TableName.of("entity_a"))
            .build();

    private static final Entity ENTITY_B = Entity.builder()
            .name(EntityName.of("entityB"))
            .pathSegment(PathSegmentName.of("entity-b"))
            .linkName(LinkName.of("entity-b"))
            .table(TableName.of("entity_b"))
            .build();

    private static final List<RelationArgumentFactory> FACTORIES = List.of(
            new SourceOneToOneRelationArgumentFactory(ENTITY_A.getName()),
            new TargetOneToOneRelationArgumentFactory(ENTITY_A.getName()),
            new ManyToOneRelationArgumentFactory(ENTITY_A.getName()),
            new OneToManyRelationArgumentFactory(ENTITY_A.getName()),
            new ManyToManyRelationArgumentFactory(ENTITY_A.getName())
    );

    private static final List<Function<RelationArgumentFactory, RelationArgumentFactory>> MODIFIERS = cartesianProduct(List.of(
            Set.of(
                    f -> f.withName("other-referencing").withTarget(ENTITY_B.getName()),
                    f -> f.withName("self-referencing").withTarget(ENTITY_A.getName())
            ),
            Set.of(
                    f -> f.withName("uni-directional"),
                    f -> f.withName("bi-directional").withBidirectional("target")
            ),
            Set.of(
                    f -> f.withName("optional"),
                    f -> f.withName("required").withRequired()
            )
    ));

    private static List<Function<RelationArgumentFactory, RelationArgumentFactory>> cartesianProduct(List<Set<Function<RelationArgumentFactory, RelationArgumentFactory>>> modifiers) {
        return Sets.cartesianProduct(modifiers).stream()
                .map(combination -> combination.stream().reduce(Function.identity(), Function::andThen))
                .toList();
    }

    static Stream<Arguments> relations() {
        return FACTORIES.stream()
                .flatMap(factory -> MODIFIERS.stream().map(modifier -> modifier.apply(factory)))
                .flatMap(f -> {
                    try {
                        return Stream.of(f.build());
                    } catch (UnbuildableException e) {
                        return Stream.empty();
                    }
                });
    }

    static Stream<Arguments> relations(Predicate<Relation> filter) {
        return relations()
                .filter(args -> filter.test((Relation) args.get()[0]));
    }

    static Stream<Arguments> toOneRelations() {
        return relations(rel -> targetPlurality(rel) == Plurality.ONE);
    }

    public static Stream<Arguments> toManyRelations() {
        return relations(rel -> targetPlurality(rel) == Plurality.MANY);
    }

    static Stream<Arguments> fromOneRelations() {
        return relations(rel -> sourcePlurality(rel) == Plurality.ONE);
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

    enum Plurality {
        ONE,
        MANY
    }

    static Plurality targetPlurality(Relation relation) {
        return switch (relation) {
            case OneToOneRelation ignored -> Plurality.ONE;
            case ManyToOneRelation ignored -> Plurality.ONE;
            case ManyToManyRelation ignored -> Plurality.MANY;
            case OneToManyRelation ignored -> Plurality.MANY;
        };
    }

    static Plurality sourcePlurality(Relation relation) {
        return switch(relation) {
            case OneToOneRelation ignored -> Plurality.ONE;
            case OneToManyRelation ignored -> Plurality.ONE;
            case ManyToOneRelation ignored -> Plurality.MANY;
            case ManyToManyRelation ignored -> Plurality.MANY;
        };
    }

    @ParameterizedTest
    @MethodSource("relations")
    void createEntityWithRelation_success(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked when creating the target")
                .isFalse();

        var app = createModel(relation);
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        var relationData = switch (targetPlurality(relation)) {
            case ONE -> XToOneRelationData.builder()
                    .name(relation.getSourceEndPoint().getName())
                    .ref(target.getId())
                    .build();
            case MANY -> XToManyRelationData.builder()
                    .name(relation.getSourceEndPoint().getName())
                    .ref(target.getId())
                    .build();
        };

        var created = queryEngine.create(app, EntityCreateData.builder()
                .entityName(relation.getSourceEndPoint().getEntity())
                .relation(relationData)
                .build(), PERMIT_ALWAYS, NONE_EVENTS);

        assertThatLinked(app, relation, created.getIdentity(), target.getIdentity());
    }

    @ParameterizedTest
    @MethodSource("relations")
    void createEntityWithRelation_nonExisting(Relation relation) {
        var app = createModel(relation);

        var nonExisting = EntityId.of(UUID.randomUUID());

        var relationData = switch (targetPlurality(relation)) {
            case ONE -> XToOneRelationData.builder()
                    .name(relation.getSourceEndPoint().getName())
                    .ref(nonExisting)
                    .build();
            case MANY -> XToManyRelationData.builder()
                    .name(relation.getSourceEndPoint().getName())
                    .ref(nonExisting)
                    .build();
        };

        var createData = EntityCreateData.builder()
                .entityName(relation.getSourceEndPoint().getEntity())
                .relation(relationData)
                .build();

        assertThatThrownBy(() -> queryEngine.create(app, createData, PERMIT_ALWAYS, NONE_EVENTS))
                .isInstanceOfSatisfying(EntityIdNotFoundException.class, ex -> {
                    assertThat(ex.getEntityName()).isEqualTo(relation.getTargetEndPoint().getEntity());
                    assertThat(ex.getId()).isEqualTo(nonExisting);
                });
    }

    @ParameterizedTest
    @MethodSource("relations")
    void linkRelation_noVersionCheck_success(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();
        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        var relationRequest = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());

        switch(targetPlurality(relation)) {
            case ONE -> queryEngine.setLink(app, relationRequest, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
            case MANY -> queryEngine.addLinks(app, relationRequest, Set.of(target.getId()), PERMIT_ALWAYS, NONE_EVENTS);
        }

        assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void linkRelation_versionCheck_success(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();

        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        var relationRequest = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());

        var relationVersion = queryEngine.findTarget(app, relationRequest, PERMIT_ALWAYS)
                .map(EntityIdAndVersion::version)
                .orElse(Version.nonExisting());

        queryEngine.setLink(app, relationRequest.withVersionConstraint(relationVersion), target.getId(), PERMIT_ALWAYS, NONE_EVENTS);

        assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void linkRelation_versionCheck_failure(Relation relation) {
        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        var relationRequest = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());

        var actualVersion = queryEngine.findTarget(app, relationRequest, PERMIT_ALWAYS)
                .map(EntityIdAndVersion::version)
                .orElse(Version.nonExisting());

        var relationVersion = Version.exactly("not-my-version");

        assertThatThrownBy(() -> queryEngine.setLink(app, relationRequest.withVersionConstraint(relationVersion), target.getId(), PERMIT_ALWAYS, NONE_EVENTS))
                .isInstanceOfSatisfying(UnsatisfiedVersionException.class, ex -> {
                    assertThat(ex.getActualVersion()).isEqualTo(actualVersion);
                    assertThat(ex.getRequestedVersion()).isEqualTo(relationVersion);
                });

        // Now swap the existence/non-existence around from the actual
        var existenceCheckRelationVersion = switch (actualVersion) {
            case NonExistingVersion ignored -> Version.exactly("I would like this to exist");
            case UnspecifiedVersion ignored -> Version.nonExisting();
            case ExactlyVersion ignored -> Version.nonExisting();
        };

        assertThatThrownBy(() -> queryEngine.setLink(app, relationRequest.withVersionConstraint(existenceCheckRelationVersion), target.getId(), PERMIT_ALWAYS, NONE_EVENTS))
                .isInstanceOfSatisfying(UnsatisfiedVersionException.class, ex -> {
                    assertThat(ex.getActualVersion()).isEqualTo(actualVersion);
                    assertThat(ex.getRequestedVersion()).isEqualTo(existenceCheckRelationVersion);
                });
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void linkRelation_relink_success(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();

        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        var relationRequest = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());

        queryEngine.setLink(app, relationRequest, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);

        var newTarget = createItem(app, relation.getTargetEndPoint().getEntity());

        queryEngine.setLink(app, relationRequest, newTarget.getId(), PERMIT_ALWAYS, NONE_EVENTS);

        assertThatLinked(app, relation, source.getIdentity(), newTarget.getIdentity());
        assertThatNotLinked(app, relation, source.getIdentity(), target.getIdentity());
    }

    @ParameterizedTest
    @MethodSource("toManyRelations")
    void linkRelation_many_success(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();
        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var relationRequest = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());
        var targets = Stream.iterate(0, i -> i + 1)
                .limit(10)
                .map(unused -> createItem(app, relation.getTargetEndPoint().getEntity()))
                .toList();

        queryEngine.addLinks(app, relationRequest, targets.stream().map(EntityData::getId).collect(Collectors.toUnmodifiableSet()), PERMIT_ALWAYS, NONE_EVENTS);

        for (var target : targets) {
            assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());
        }
    }

    @ParameterizedTest
    @MethodSource("toManyRelations")
    void linkRelation_many_failure(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();
        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var relationRequest = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());
        var targets = Stream.iterate(0, i -> i + 1)
                .limit(10)
                .map(unused -> createItem(app, relation.getTargetEndPoint().getEntity()))
                .toList();

        // One of the targets in the middle doesn't exist anymore
        queryEngine.delete(app, targets.get(4).getIdentity().toRequest(), PERMIT_ALWAYS, NONE_EVENTS);

        assertThatThrownBy(() -> {
            queryEngine.addLinks(app, relationRequest, targets.stream().map(EntityData::getId).collect(Collectors.toUnmodifiableSet()), PERMIT_ALWAYS, NONE_EVENTS);
        }).isInstanceOfSatisfying(EntityIdNotFoundException.class, ex -> {
            assertThat(ex.getEntityName()).isEqualTo(relation.getTargetEndPoint().getEntity());
            assertThat(ex.getId()).isEqualTo(targets.get(4).getId());
        });

        // Nothing should be linked then
        for (var target : targets) {
            try {
                assertThatNotLinked(app, relation, source.getIdentity(), target.getIdentity());
            } catch(EntityIdNotFoundException e) {
                // Ignore a not found when it's about the target that we deleted
                // We need to do this because the assert checks both sides of the relation,
                // and using the deleted target as source side, we'll get an exception
                if(!Objects.equals(e.getId(), targets.get(4).getId())) {
                    throw e;
                }
            }
        }
    }
    @ParameterizedTest
    @MethodSource("fromOneRelations")
    void linkRelation_blindOverwrite_failure(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();
        var app = createModel(relation);

        var originalSource = createItem(app, relation.getSourceEndPoint().getEntity());
        var originalSourceRelReq = RelationRequest.forRelation(originalSource.getIdentity(), relation.getSourceEndPoint().getName());
        var newSource = createItem(app, relation.getSourceEndPoint().getEntity());
        var newSourceRelReq = RelationRequest.forRelation(newSource.getIdentity(), relation.getSourceEndPoint().getName());

        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        if(!relation.getTargetEndPoint().isRequired()) {
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.setLink(app, originalSourceRelReq, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                case MANY -> queryEngine.addLinks(app, originalSourceRelReq, Set.of(target.getId()), PERMIT_ALWAYS,
                        NONE_EVENTS);
            }
        }

        assertThatThrownBy(() -> {
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.setLink(app, newSourceRelReq, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                case MANY -> queryEngine.addLinks(app, newSourceRelReq, Set.of(target.getId()), PERMIT_ALWAYS, NONE_EVENTS);
            }
        }).isInstanceOfSatisfying(BlindRelationOverwriteException.class, ex  -> {
            assertThat(ex.getExistingRelation()).isEqualTo(RelationIdentity.forRelation(originalSource.getIdentity(), relation.getSourceEndPoint().getName()));
            assertThat(ex.getNewRelation()).isEqualTo(RelationIdentity.forRelation(newSource.getIdentity(), relation.getSourceEndPoint().getName()));
            assertThat(ex.getTargetEntity()).isEqualTo(target.getIdentity());
        });
    }

    @ParameterizedTest
    @MethodSource("relations")
    void linkRelation_targetNonExistent_failure(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();
        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var relationRequest = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());

        var nonExistentTarget = EntityId.of(UUID.randomUUID());

        assertThatThrownBy(() -> {
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.setLink(app, relationRequest, nonExistentTarget, PERMIT_ALWAYS, NONE_EVENTS);
                case MANY -> queryEngine.addLinks(app, relationRequest, Set.of(nonExistentTarget), PERMIT_ALWAYS,
                        NONE_EVENTS);
            }
        }).isInstanceOfSatisfying(EntityIdNotFoundException.class, ex -> {
            assertThat(ex.getEntityName()).isEqualTo(relation.getTargetEndPoint().getEntity());
            assertThat(ex.getId()).isEqualTo(nonExistentTarget);
        });
    }

    @ParameterizedTest
    @MethodSource("relations")
    void linkRelation_sourceNonExistent_failure(Relation relation) {
        var app = createModel(relation);

        var nonExistentSource = EntityId.of(UUID.randomUUID());
        var relationRequest = RelationRequest.forRelation(relation.getSourceEndPoint().getEntity(), nonExistentSource, relation.getSourceEndPoint().getName());

        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        assertThatThrownBy(() -> {
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.setLink(app, relationRequest, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                case MANY -> queryEngine.addLinks(app, relationRequest, Set.of(target.getId()), PERMIT_ALWAYS,
                        NONE_EVENTS);
            }
        }).isInstanceOfSatisfying(EntityIdNotFoundException.class, ex -> {
            assertThat(ex.getEntityName()).isEqualTo(relation.getSourceEndPoint().getEntity());
            assertThat(ex.getId()).isEqualTo(nonExistentSource);
        });
    }

    @ParameterizedTest
    @MethodSource("relations")
    void unlinkRelation_noVersionCheck_success(Relation relation) {
        assumeThat(relation.getSourceEndPoint().isRequired() || relation.getTargetEndPoint().isRequired())
                .as("can not unlink a relation that is required on the either side")
                .isFalse();
        var app = createModel(relation);
        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var sourceRelReq = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        switch (targetPlurality(relation)) {
            case ONE -> queryEngine.setLink(app,sourceRelReq, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
            case MANY -> queryEngine.addLinks(app, sourceRelReq, Set.of(target.getId()), PERMIT_ALWAYS,
                    NONE_EVENTS);
        }

        assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());

        switch (targetPlurality(relation)) {
            case ONE -> queryEngine.unsetLink(app, sourceRelReq, PERMIT_ALWAYS, NONE_EVENTS);
            case MANY -> queryEngine.removeLinks(app, sourceRelReq, Set.of(target.getId()), PERMIT_ALWAYS, NONE_EVENTS);
        }

        assertThatNotLinked(app, relation, source.getIdentity(), target.getIdentity());
    }

    @ParameterizedTest
    @MethodSource("toOneRelations")
    void unlinkRelation_versionCheck_success(Relation relation) {
        assumeThat(relation.getSourceEndPoint().isRequired() || relation.getTargetEndPoint().isRequired())
                .as("can not unlink a relation that is required on the either side")
                .isFalse();
        var app = createModel(relation);
        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var sourceRelReq = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        queryEngine.setLink(app,sourceRelReq, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);

        var relationVersion = queryEngine.findTarget(app, sourceRelReq, PERMIT_ALWAYS)
                .map(EntityIdAndVersion::version)
                .orElse(Version.nonExisting());

        assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());

        queryEngine.unsetLink(app, sourceRelReq.withVersionConstraint(relationVersion), PERMIT_ALWAYS, NONE_EVENTS);

        assertThatNotLinked(app, relation, source.getIdentity(), target.getIdentity());
    }


    @ParameterizedTest
    @MethodSource("toOneRelations")
    void unlinkRelation_versionCheck_failure(Relation relation) {
        assumeThat(relation.getSourceEndPoint().isRequired() || relation.getTargetEndPoint().isRequired())
                .as("can not unlink a relation that is required on the either side")
                .isFalse();
        var app = createModel(relation);
        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var sourceRelReq = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        queryEngine.setLink(app,sourceRelReq, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);

        var actualVersion = queryEngine.findTarget(app, sourceRelReq, PERMIT_ALWAYS)
                .map(EntityIdAndVersion::version)
                .orElse(Version.nonExisting());

        var relationVersion = Version.exactly("not-my-version");
        assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());

        assertThatThrownBy(() -> queryEngine.unsetLink(app, sourceRelReq.withVersionConstraint(relationVersion), PERMIT_ALWAYS, NONE_EVENTS))
                .isInstanceOfSatisfying(UnsatisfiedVersionException.class, ex -> {
                    assertThat(ex.getActualVersion()).isEqualTo(actualVersion);
                    assertThat(ex.getRequestedVersion()).isEqualTo(relationVersion);
                });

        // Now swap the existence/non-existence around from the actual
        var existenceCheckRelationVersion = switch (actualVersion) {
            case NonExistingVersion ignored -> Version.exactly("I would like this to exist");
            case UnspecifiedVersion ignored -> Version.nonExisting();
            case ExactlyVersion ignored -> Version.nonExisting();
        };

        assertThatThrownBy(() -> queryEngine.unsetLink(app, sourceRelReq.withVersionConstraint(existenceCheckRelationVersion), PERMIT_ALWAYS, NONE_EVENTS))
                .isInstanceOfSatisfying(UnsatisfiedVersionException.class, ex -> {
                    assertThat(ex.getActualVersion()).isEqualTo(actualVersion);
                    assertThat(ex.getRequestedVersion()).isEqualTo(existenceCheckRelationVersion);
                });

        assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());
    }

    @ParameterizedTest
    @MethodSource("relations")
    void unlinkRelation_required_failure(Relation relation) {
        assumeThat(relation.getSourceEndPoint().isRequired() || relation.getTargetEndPoint().isRequired())
                .isTrue();

        var app = createModel(relation);
        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var sourceRelReq = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        if (relation.getTargetEndPoint().isRequired()) {
            var targetRelReq = RelationRequest.forRelation(target.getIdentity(), relation.getTargetEndPoint().getName());
            // If the target endpoint is required, a link was already set up during create.
            // We have to set the link from the target side, otherwise it will be a BlindRelationOverwriteException
            queryEngine.setLink(app, targetRelReq, source.getId(), PERMIT_ALWAYS, NONE_EVENTS);
        } else {
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.setLink(app, sourceRelReq, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                case MANY -> queryEngine.addLinks(app, sourceRelReq, Set.of(target.getId()), PERMIT_ALWAYS, NONE_EVENTS);
            }
        }

        var thrown = assertThatThrownBy(() -> {
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.unsetLink(app, sourceRelReq, PERMIT_ALWAYS, NONE_EVENTS);
                case MANY -> queryEngine.removeLinks(app, sourceRelReq, Set.of(target.getId()), PERMIT_ALWAYS, NONE_EVENTS);
            }
        });
        if(relation.getSourceEndPoint().isRequired()) {
            thrown.isInstanceOfSatisfying(RequiredConstraintViolationException.class, ex -> {
                assertThat(ex.getEntityIdentity()).isEqualTo(source.getIdentity());
                assertThat(ex.getPropertyPath()).isEqualTo(
                        new RelationPath(relation.getSourceEndPoint().getName(), null));
            });
        }
        if(relation.getTargetEndPoint().isRequired()) {
            thrown.isInstanceOfSatisfying(EntityLinkedByRequiredRelationException.class, ex  -> {
                assertThat(ex.getSourceIdentity()).isEqualTo(source.getIdentity());
                assertThat(ex.getTargetRelationIdentity()).isEqualTo(RelationIdentity.forRelation(target.getIdentity(), relation.getTargetEndPoint()
                        .getName()));
            });
        }
    }

    @ParameterizedTest
    @MethodSource("toManyRelations")
    void unlinkRelation_clearAll_success(Relation relation) {
        assumeThat(relation.getTargetEndPoint().isRequired())
                .as("if target has a required relation, an item would already be linked and can't be overwritten from the source side")
                .isFalse();
        var app = createModel(relation);
        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var sourceRelReq = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());

        var target1 = createItem(app, relation.getTargetEndPoint().getEntity());
        var target2 = createItem(app, relation.getTargetEndPoint().getEntity());

        queryEngine.addLinks(app, sourceRelReq, Set.of(target1.getId(), target2.getId()), PERMIT_ALWAYS, NONE_EVENTS);

        assertThatLinked(app, relation, source.getIdentity(), target1.getIdentity());
        assertThatLinked(app, relation, source.getIdentity(), target2.getIdentity());

        queryEngine.unsetLink(app, sourceRelReq, PERMIT_ALWAYS, NONE_EVENTS);

        assertThatNotLinked(app, relation, source.getIdentity(), target1.getIdentity());
        assertThatNotLinked(app, relation, source.getIdentity(), target2.getIdentity());
    }

    @ParameterizedTest
    @MethodSource("relations")
    void unlinkRelation_clearAll_required_failure(Relation relation) {
        assumeThat(relation.getSourceEndPoint().isRequired() || relation.getTargetEndPoint().isRequired())
                .isTrue();

        var app = createModel(relation);
        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var sourceRelReq = RelationRequest.forRelation(source.getIdentity(), relation.getSourceEndPoint().getName());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        if (relation.getTargetEndPoint().isRequired()) {
            var targetRelReq = RelationRequest.forRelation(target.getIdentity(), relation.getTargetEndPoint().getName());
            // If the target endpoint is required, a link was already set up during create.
            // We have to set the link from the target side, otherwise it will be a BlindRelationOverwriteException
            queryEngine.setLink(app, targetRelReq, source.getId(), PERMIT_ALWAYS, NONE_EVENTS);
        } else {
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.setLink(app, sourceRelReq, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                case MANY -> queryEngine.addLinks(app, sourceRelReq, Set.of(target.getId()), PERMIT_ALWAYS, NONE_EVENTS);
            }
        }

        var thrown = assertThatThrownBy(() -> {
            queryEngine.unsetLink(app, sourceRelReq, PERMIT_ALWAYS, NONE_EVENTS);
        });
        if(relation.getSourceEndPoint().isRequired()) {
            thrown.isInstanceOfSatisfying(RequiredConstraintViolationException.class, ex -> {
                assertThat(ex.getEntityIdentity()).isEqualTo(source.getIdentity());
                assertThat(ex.getPropertyPath()).isEqualTo(
                        new RelationPath(relation.getSourceEndPoint().getName(), null));
            });
        }
        if(relation.getTargetEndPoint().isRequired()) {
            thrown.isInstanceOfSatisfying(EntityLinkedByRequiredRelationException.class, ex  -> {
                assertThat(ex.getSourceIdentity()).isEqualTo(source.getIdentity());
                assertThat(ex.getTargetRelationIdentity()).isEqualTo(RelationIdentity.forRelation(target.getIdentity(), relation.getTargetEndPoint()
                        .getName()));
            });
        }
    }

    @ParameterizedTest
    @MethodSource("relations")
    void deleteEntityWithRelation(Relation relation) {
        var app = createModel(relation);

        var source = createItem(app, relation.getSourceEndPoint().getEntity());
        var target = createItem(app, relation.getTargetEndPoint().getEntity());

        if(relation.getTargetEndPoint().isRequired()) {
            var targetRelReq = RelationRequest.forRelation(target.getIdentity(), relation.getTargetEndPoint().getName());
            // If the target endpoint is required, a link was already set up during create.
            // We have to set the link from the target side, otherwise it will be a BlindRelationOverwriteException
            queryEngine.setLink(app, targetRelReq, source.getId(), PERMIT_ALWAYS, NONE_EVENTS);
        } else {
            var relationRequest = RelationRequest.forRelation(source.getIdentity(),
                    relation.getSourceEndPoint().getName());
            switch (targetPlurality(relation)) {
                case ONE -> queryEngine.setLink(app, relationRequest, target.getId(), PERMIT_ALWAYS, NONE_EVENTS);
                case MANY ->
                        queryEngine.addLinks(app, relationRequest, Set.of(target.getId()), PERMIT_ALWAYS, NONE_EVENTS);
            }
        }

        var thrown = assertThatCode(() -> queryEngine.delete(app, source.getIdentity().toRequest(), PERMIT_ALWAYS, NONE_EVENTS));

        if(relation.getTargetEndPoint().isRequired()) {
            // if target is required relation, the source that references it can't be deleted
            thrown.isInstanceOfSatisfying(EntityLinkedByRequiredRelationException.class, ex -> {
                assertThat(ex.getSourceIdentity()).isEqualTo(source.getIdentity());
                assertThat(ex.getTargetRelationIdentity())
                        .isEqualTo(RelationIdentity.forRelation(target.getIdentity(), relation.getTargetEndPoint().getName()));
            });

            // entity has not been deleted and is still linked
            assertThat(queryEngine.findById(app, source.getIdentity().toRequest(), PERMIT_ALWAYS)).isPresent();
            assertThatLinked(app, relation, source.getIdentity(), target.getIdentity());
        } else {
            thrown.doesNotThrowAnyException();
            assertThat(queryEngine.findById(app, source.getIdentity().toRequest(), PERMIT_ALWAYS)).isEmpty();
        }

    }

    private void assertThatLinked(Application app, Relation relation, EntityIdentity source, EntityIdentity target) {
        assertThatLinked(app, relation, source, target, true);
    }

    private void assertThatNotLinked(Application app, Relation relation, EntityIdentity source, EntityIdentity target) {
        assertThatLinked(app, relation, source, target, false);
    }

    private void assertThatLinked(Application app, Relation relation, EntityIdentity source, EntityIdentity target,
            boolean isLinked) {
        assertThat(queryEngine.isLinked(
                app,
                RelationRequest.forRelation(source, relation.getSourceEndPoint().getName()),
                target.getEntityId(),
                PERMIT_ALWAYS
        )).isEqualTo(isLinked);

        if(relation.getTargetEndPoint().getName() != null) {
            assertThat(queryEngine.isLinked(
                    app,
                    RelationRequest.forRelation(target, relation.getTargetEndPoint().getName()),
                    source.getEntityId(),
                    PERMIT_ALWAYS
            )).isEqualTo(isLinked);
        }
    }

    interface RelationArgumentFactory {
        RelationArgumentFactory withName(String name);
        RelationArgumentFactory withTarget(EntityName target);
        RelationArgumentFactory withBidirectional(String name);
        RelationArgumentFactory withRequired();

        Arguments build() throws UnbuildableException;

        class UnbuildableException extends Exception {

        }
    }

    @RequiredArgsConstructor
    abstract static class AbstractRelationArgumentFactory<T extends Relation> implements RelationArgumentFactory {
        private static final Consumer<RelationEndPointBuilder> SOURCE_NAMES = b -> b.name(RelationName.of("rel_src"))
                .linkName(LinkName.of("rel-src"))
                .pathSegment(PathSegmentName.of("rel-src"));

        @NonNull
        protected final Consumer<RelationEndPointBuilder> sourceCustomizer;
        @NonNull
        protected final Consumer<RelationEndPointBuilder> targetCustomizer;
        @NonNull
        protected final String name;

        protected abstract RelationArgumentFactory copyWith(Consumer<RelationEndPointBuilder> sourceCustomizer, Consumer<RelationEndPointBuilder> targetCustomizer, String name);
        protected abstract T createRelation(RelationEndPoint source, RelationEndPoint target);

        @Override
        public RelationArgumentFactory withTarget(EntityName target) {
            return copyWith(
                    sourceCustomizer,
                    targetCustomizer.andThen(b -> b.entity(target)),
                    name
            );
        }

        @Override
        public RelationArgumentFactory withName(String name) {
            return copyWith(
                    sourceCustomizer,
                    targetCustomizer,
                    name + " " + this.name
            );
        }

        @Override
        public RelationArgumentFactory withBidirectional(String relName) {
            return copyWith(
                    sourceCustomizer,
                    targetCustomizer.andThen(b -> b
                            .clearFlags()
                            .name(RelationName.of(relName))
                            .linkName(LinkName.of(relName))
                            .pathSegment(PathSegmentName.of(relName))
                    ),
                    name
            );
        }

        private static RelationEndPoint createEndpoint(Consumer<RelationEndPointBuilder> customizer) {
            var builder = RelationEndPoint.builder();
            customizer.accept(builder);
            return builder.build();
        }


        public Arguments build() throws UnbuildableException {
            var sourceEndpoint = createEndpoint(SOURCE_NAMES.andThen(sourceCustomizer));
            var targetEndpoint = createEndpoint(targetCustomizer);
            if(Objects.equals(sourceEndpoint.getEntity(), targetEndpoint.getEntity()) &&
                    (sourceEndpoint.isRequired() || targetEndpoint.isRequired())) {
                // Can't build a relation that is self-referencing and required
                throw new UnbuildableException();
            }

            if(Objects.isNull(sourceEndpoint.getName()) && sourceEndpoint.isRequired()) {
                throw new UnbuildableException();
            }

            if(Objects.isNull(targetEndpoint.getName()) && targetEndpoint.isRequired()) {
                throw new UnbuildableException();
            }
            var relation = createRelation(
                    sourceEndpoint,
                    targetEndpoint
            );
            return Arguments.argumentSet(name, relation);
        }

    }

    abstract static class XToOneRelationArgumentFactory<T extends Relation> extends AbstractRelationArgumentFactory<T> {

        public XToOneRelationArgumentFactory(
                @NonNull Consumer<RelationEndPointBuilder> sourceCustomizer,
                @NonNull Consumer<RelationEndPointBuilder> targetCustomizer,
                @NonNull String name
        ) {
            super(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        public RelationArgumentFactory withRequired() {
            return copyWith(
                    sourceCustomizer.andThen(b -> b.flag(RequiredEndpointFlag.INSTANCE)),
                    targetCustomizer,
                    name
            );
        }
    }

    static class SourceOneToOneRelationArgumentFactory extends XToOneRelationArgumentFactory<SourceOneToOneRelation> {

        public SourceOneToOneRelationArgumentFactory(EntityName source) {
            this(b -> b.entity(source), b -> {}, "source one-to-one");
        }

        private SourceOneToOneRelationArgumentFactory(@NonNull Consumer<RelationEndPointBuilder> sourceCustomizer,
                @NonNull Consumer<RelationEndPointBuilder> targetCustomizer, @NonNull String name) {
            super(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        protected SourceOneToOneRelationArgumentFactory copyWith(Consumer<RelationEndPointBuilder> sourceCustomizer,
                Consumer<RelationEndPointBuilder> targetCustomizer, String name) {
            return new SourceOneToOneRelationArgumentFactory(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        protected SourceOneToOneRelation createRelation(RelationEndPoint source, RelationEndPoint target) {
            return SourceOneToOneRelation.builder()
                    .sourceEndPoint(source)
                    .targetEndPoint(target)
                    .targetReference(ColumnName.of("rel"))
                    .build();
        }

    }

    static class TargetOneToOneRelationArgumentFactory extends XToOneRelationArgumentFactory<TargetOneToOneRelation> {

        public TargetOneToOneRelationArgumentFactory(EntityName source) {
            this(b -> b.entity(source), b -> {}, "target one-to-one");
        }

        private TargetOneToOneRelationArgumentFactory(@NonNull Consumer<RelationEndPointBuilder> sourceCustomizer,
                @NonNull Consumer<RelationEndPointBuilder> targetCustomizer, @NonNull String name) {
            super(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        public RelationArgumentFactory withRequired() {
            return copyWith(
                    sourceCustomizer,
                    targetCustomizer.andThen(b -> b.flag(RequiredEndpointFlag.INSTANCE)),
                    name
            );
        }

        @Override
        protected TargetOneToOneRelationArgumentFactory copyWith(Consumer<RelationEndPointBuilder> sourceCustomizer,
                Consumer<RelationEndPointBuilder> targetCustomizer, String name) {
            return new TargetOneToOneRelationArgumentFactory(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        protected TargetOneToOneRelation createRelation(RelationEndPoint source, RelationEndPoint target) {
            return TargetOneToOneRelation.builder()
                    .sourceEndPoint(source)
                    .targetEndPoint(target)
                    .sourceReference(ColumnName.of("rel"))
                    .build();
        }
    }

    static class ManyToOneRelationArgumentFactory extends XToOneRelationArgumentFactory<ManyToOneRelation> {
        public ManyToOneRelationArgumentFactory(EntityName source) {
            this(b -> b.entity(source), b -> {}, "many-to-one");
        }

        private ManyToOneRelationArgumentFactory(@NonNull Consumer<RelationEndPointBuilder> sourceCustomizer,
                @NonNull Consumer<RelationEndPointBuilder> targetCustomizer, @NonNull String name) {
            super(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        protected ManyToOneRelationArgumentFactory copyWith(Consumer<RelationEndPointBuilder> sourceCustomizer,
                Consumer<RelationEndPointBuilder> targetCustomizer, String name) {
            return new ManyToOneRelationArgumentFactory(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        protected ManyToOneRelation createRelation(RelationEndPoint source, RelationEndPoint target) {
            return ManyToOneRelation.builder()
                    .sourceEndPoint(source)
                    .targetEndPoint(target)
                    .targetReference(ColumnName.of("rel"))
                    .build();
        }
    }

    static class ManyToManyRelationArgumentFactory extends AbstractRelationArgumentFactory<ManyToManyRelation> {
        public ManyToManyRelationArgumentFactory(EntityName source) {
            this(b -> b.entity(source), b -> b.name(RelationName.of("rel-tgt")).flag(HiddenEndpointFlag.INSTANCE), "many-to-many");
        }

        private ManyToManyRelationArgumentFactory(@NonNull Consumer<RelationEndPointBuilder> sourceCustomizer,
                @NonNull Consumer<RelationEndPointBuilder> targetCustomizer, @NonNull String name) {
            super(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        public RelationArgumentFactory withRequired() {
            return new UnsupportedRelationArgumentFactory();
        }

        @Override
        protected RelationArgumentFactory copyWith(Consumer<RelationEndPointBuilder> sourceCustomizer,
                Consumer<RelationEndPointBuilder> targetCustomizer, String name) {
            return new ManyToManyRelationArgumentFactory(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        protected ManyToManyRelation createRelation(RelationEndPoint source, RelationEndPoint target) {
            return ManyToManyRelation.builder()
                    .sourceEndPoint(source)
                    .targetEndPoint(target)
                    .joinTable(TableName.of("rel"))
                    .sourceReference(ColumnName.of("src"))
                    .targetReference(ColumnName.of("tgt"))
                    .build();
        }
    }

    static class OneToManyRelationArgumentFactory extends AbstractRelationArgumentFactory<OneToManyRelation> {
        public OneToManyRelationArgumentFactory(EntityName source) {
            this(b -> b.entity(source), b -> b.name(RelationName.of("rel-tgt")).flag(HiddenEndpointFlag.INSTANCE), "one-to-many");
        }

        private OneToManyRelationArgumentFactory(@NonNull Consumer<RelationEndPointBuilder> sourceCustomizer,
                @NonNull Consumer<RelationEndPointBuilder> targetCustomizer, @NonNull String name) {
            super(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        public RelationArgumentFactory withRequired() {
            return copyWith(
                    sourceCustomizer,
                    targetCustomizer.andThen(b -> b.flag(RequiredEndpointFlag.INSTANCE)),
                    name
            );
        }

        @Override
        protected RelationArgumentFactory copyWith(Consumer<RelationEndPointBuilder> sourceCustomizer,
                Consumer<RelationEndPointBuilder> targetCustomizer, String name) {
            return new OneToManyRelationArgumentFactory(sourceCustomizer, targetCustomizer, name);
        }

        @Override
        protected OneToManyRelation createRelation(RelationEndPoint source, RelationEndPoint target) {
            return OneToManyRelation.builder()
                    .sourceEndPoint(source)
                    .targetEndPoint(target)
                    .sourceReference(ColumnName.of("rel"))
                    .build();
        }
    }

    static class UnsupportedRelationArgumentFactory implements RelationArgumentFactory {

        @Override
        public RelationArgumentFactory withName(String name) {
            return this;
        }

        @Override
        public RelationArgumentFactory withTarget(EntityName target) {
            return this;
        }

        @Override
        public RelationArgumentFactory withBidirectional(String name) {
            return this;
        }

        @Override
        public RelationArgumentFactory withRequired() {
            return this;
        }

        @Override
        public Arguments build() throws UnbuildableException {
            throw new UnbuildableException();
        }
    }

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(JOOQQueryEngineTest.TestApplication.class, args);
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
        public QueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver,
                PlatformTransactionManager transactionManager) {
            return new TransactionalQueryEngine(
                    new JOOQQueryEngine(dslContextResolver, new JOOQTimedCountStrategy(Duration.ofMillis(500))),
                    transactionManager
            );
        }
    }

    private static class NoneEvents implements CreateEventConsumer, DeleteEventConsumer, LinkEventConsumer,
            UnlinkEventConsumer, UpdateEventConsumer {

        @Override
        public void onEntityCreate(Application application, EntityData data) {
        }

        @Override
        public void onEntityDelete(Application application, EntityData data) {

        }

        @Override
        public void onLink(Application application, EntityData oldData, EntityData newData) {

        }

        @Override
        public void onUnlink(Application application, EntityData oldData, EntityData newData) {

        }

        @Override
        public void onEntityUpdate(Application application, EntityData oldData, EntityData newData) {

        }
    }
}
