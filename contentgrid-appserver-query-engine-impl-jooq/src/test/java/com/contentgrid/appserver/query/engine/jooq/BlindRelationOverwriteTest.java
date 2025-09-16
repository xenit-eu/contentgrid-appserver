package com.contentgrid.appserver.query.engine.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.TargetOneToOneRelation;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.values.EntityIdentity;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.exception.BlindRelationOverwriteException;
import com.contentgrid.appserver.query.engine.jooq.BlindRelationOverwriteTest.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQTimedCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.Scalar;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///",
        "logging.level.org.jooq.tools.LoggerListener=DEBUG"
})
@ContextConfiguration(classes = {TestApplication.class})
class BlindRelationOverwriteTest {

    public static final Scalar<Boolean> PERMIT_ALWAYS = Scalar.of(true);
    @Autowired
    private QueryEngine queryEngine;

    @Autowired
    private TableCreator tableCreator;

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

    private static final Relation SOURCE_ONE_TO_ONE = SourceOneToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_A.getName())
                    .name(RelationName.of("soto_entity_b"))
                    .pathSegment(PathSegmentName.of("soto-entity_b"))
                    .linkName(LinkName.of("soto-entity_b"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_B.getName())
                    .name(RelationName.of("soto_entity_a"))
                    .pathSegment(PathSegmentName.of("soto-entity_a"))
                    .linkName(LinkName.of("soto-entity_a"))
                    .build())
            .targetReference(ColumnName.of("soto_entity_b"))
            .build();

    private static final Relation TARGET_ONE_TO_ONE = TargetOneToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_A.getName())
                    .name(RelationName.of("toto_entity_b"))
                    .pathSegment(PathSegmentName.of("toto-entity_b"))
                    .linkName(LinkName.of("toto-entity_b"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_B.getName())
                    .name(RelationName.of("toto_entity_a"))
                    .pathSegment(PathSegmentName.of("toto-entity_a"))
                    .linkName(LinkName.of("toto-entity_a"))
                    .build())
            .sourceReference(ColumnName.of("toto_entity_a"))
            .build();

    private static final Relation ONE_TO_MANY = OneToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_A.getName())
                    .name(RelationName.of("otm_entity_b"))
                    .pathSegment(PathSegmentName.of("otm-entity-b"))
                    .linkName(LinkName.of("otm-entity-b"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_B.getName())
                    .name(RelationName.of("otm_entity_a"))
                    .pathSegment(PathSegmentName.of("otm-entity-a"))
                    .linkName(LinkName.of("otm-entity-a"))
                    .build())
            .sourceReference(ColumnName.of("otm_entity_a"))
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("test"))
            .entity(ENTITY_A)
            .entity(ENTITY_B)
            .relation(SOURCE_ONE_TO_ONE)
            .relation(TARGET_ONE_TO_ONE)
            .relation(ONE_TO_MANY)
            .build();

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
    }

    @AfterEach
    void cleanup() {
        tableCreator.dropTables(APPLICATION);
    }

    static Stream<Arguments> relations() {
        return Stream.of(
                Arguments.argumentSet("one-to-one (source)", SOURCE_ONE_TO_ONE),
                Arguments.argumentSet("one-to-one (target)", TARGET_ONE_TO_ONE),
                Arguments.argumentSet("one-to-many", ONE_TO_MANY)
        );
    }

    private EntityIdentity createEntity(EntityName entityName) {
        return queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(entityName)
                        .build(),
                PERMIT_ALWAYS
        ).getIdentity();
    }

    private void createLink(Relation relation, EntityIdentity source, EntityIdentity target) {
        var relationRequest = createRelationRequest(relation, source);

        if(relation instanceof OneToManyRelation) {
            queryEngine.addLinks(
                    APPLICATION,
                    relationRequest,
                    Set.of(target.getEntityId()),
                    PERMIT_ALWAYS
            );
        } else {
            queryEngine.setLink(APPLICATION, relationRequest, target.getEntityId(), PERMIT_ALWAYS);
        }
    }

    private RelationRequest createRelationRequest(Relation relation, EntityIdentity entity) {
        assert Objects.equals(relation.getSourceEndPoint().getEntity(), entity.getEntityName());
        return RelationRequest.forRelation(relation.getSourceEndPoint().getEntity(), entity.getEntityId(), relation.getSourceEndPoint()
                .getName());
    }

    /**
     * When writing to a *-to-one relation that is already linked on our side (so it would be visible by reading the relation from our side),
     * it should be possible to overwrite the value (because you can use locking to ensure you don't accidentally overwrite an unexpected value)
     */
    @ParameterizedTest(name = ParameterizedTest.DEFAULT_DISPLAY_NAME+"; inversed")
    @MethodSource("relations")
    void overwriteExistingValueOnOurSide_succeeds(Relation r) {
        var relation = r.inverse();
        var source = createEntity(relation.getSourceEndPoint().getEntity());
        var originalTarget = createEntity(relation.getTargetEndPoint().getEntity());
        var newTarget = createEntity(relation.getTargetEndPoint().getEntity());

        createLink(relation, source, originalTarget);

        var relationRequest = createRelationRequest(relation, source);

        assertThat(queryEngine.isLinked(APPLICATION, relationRequest, originalTarget.getEntityId(), PERMIT_ALWAYS)).isTrue();

        createLink(relation, source, newTarget);

        assertThat(queryEngine.isLinked(APPLICATION, relationRequest, originalTarget.getEntityId(), PERMIT_ALWAYS)).isFalse();
        assertThat(queryEngine.isLinked(APPLICATION, relationRequest, newTarget.getEntityId(), PERMIT_ALWAYS)).isTrue();
    }

    /**
     * When writing to a one-to-* relation that is not yet linked from the other side, it should be possible to write a value
     * <p>
     * There is no way to check or lock the relation from this side, but setting an initial value is safe, as we don't overwrite existing data
     */
    @ParameterizedTest
    @MethodSource("relations")
    void overwriteEmptyValueOnOtherSide_succeeds(Relation relation) {
        var target = createEntity(relation.getTargetEndPoint().getEntity());
        var newSource = createEntity(relation.getSourceEndPoint().getEntity());

        createLink(relation.inverse(), target, newSource);

        var targetRelationRequest = createRelationRequest(relation.inverse(), target);
        var newSourceRelationRequest = createRelationRequest(relation, newSource);

        assertThat(queryEngine.isLinked(APPLICATION, newSourceRelationRequest, target.getEntityId(), PERMIT_ALWAYS)).isTrue();
        assertThat(queryEngine.isLinked(APPLICATION, targetRelationRequest, newSource.getEntityId(), PERMIT_ALWAYS)).isTrue();

    }

    /**
     * When writing to a one-to-* relation that is already linked on from the other side (so it is not visible by reading the relation from our side),
     * it should NOT be possible to overwrite the value.
     * There is no way to lock the relation from this side, and we don't want to accidentally and blindly overwrite a value of the other side
     */
    @ParameterizedTest
    @MethodSource("relations")
    void overwriteExistingValueOnOtherSide_fails(Relation relation) {
        var target = createEntity(relation.getTargetEndPoint().getEntity());
        assert target.getEntityName() == ENTITY_B.getName();
        var originalSource = createEntity(relation.getSourceEndPoint().getEntity());
        assert originalSource.getEntityName() == ENTITY_A.getName();
        var newSource = createEntity(relation.getSourceEndPoint().getEntity());
        assert newSource.getEntityName() == ENTITY_A.getName();

        // Ensure that the target is linked to the original source (this is not visible from newSource)
        createLink(relation, originalSource, target);

        var targetRelationRequest = createRelationRequest(relation.inverse(), target);

        assertThat(queryEngine.isLinked(APPLICATION, targetRelationRequest, originalSource.getEntityId(), PERMIT_ALWAYS)).isTrue();


        assertThatThrownBy(() ->
                // Now try to link the new source to target. This would overwrite the original source that was linked to target
                createLink(relation, newSource, target)
        ).isInstanceOfSatisfying(BlindRelationOverwriteException.class, ex -> {
            assertThat(ex.getAffectedRelation().getEntityName()).isEqualTo(target.getEntityName());
            assertThat(ex.getAffectedRelation().getRelationName()).isEqualTo(relation.getTargetEndPoint().getName());
            assertThat(ex.getAffectedRelation().getEntityId()).isEqualTo(target.getEntityId());
            assertThat(ex.getOriginalValue()).isEqualTo(originalSource);
        });

        var newSourceRelationRequest = createRelationRequest(relation, newSource);

        assertThat(queryEngine.isLinked(APPLICATION, targetRelationRequest, originalSource.getEntityId(), PERMIT_ALWAYS)).isTrue();
        assertThat(queryEngine.isLinked(APPLICATION, targetRelationRequest, newSource.getEntityId(), PERMIT_ALWAYS)).isFalse();
        assertThat(queryEngine.isLinked(APPLICATION, newSourceRelationRequest, target.getEntityId(), PERMIT_ALWAYS)).isFalse();
    }

    @Test
    void overwriteMultipleValuesOnOtherSide_fails() {
        var relation = ONE_TO_MANY;
        var originalSource = createEntity(relation.getSourceEndPoint().getEntity());

        var targets = List.of(
                createEntity(relation.getTargetEndPoint().getEntity()),
                createEntity(relation.getTargetEndPoint().getEntity()),
                createEntity(relation.getTargetEndPoint().getEntity())
        );

        var targetIds = targets.stream().map(EntityIdentity::getEntityId).collect(Collectors.toSet());


        var originalSourceRelationRequest = createRelationRequest(relation, originalSource);

        // Link all targets to the original source
        queryEngine.addLinks(
                APPLICATION,
                originalSourceRelationRequest,
                targetIds,
                PERMIT_ALWAYS
        );

        var newSource = createEntity(relation.getSourceEndPoint().getEntity());
        var newSourceRelationRequest =createRelationRequest(relation, newSource);

        var succeedingTarget = createEntity(relation.getTargetEndPoint().getEntity());

        // Now try to link the targets to the new source
        assertThatThrownBy(() -> {
            queryEngine.addLinks(
                    APPLICATION,
                    newSourceRelationRequest,
                    Stream.concat(Stream.of(succeedingTarget.getEntityId()), targetIds.stream()).collect(Collectors.toSet()),
                    PERMIT_ALWAYS
            );
        }).isInstanceOfSatisfying(BlindRelationOverwriteException.class, ex -> {
            var allExceptions = Stream.concat(Stream.of(ex), Arrays.stream(ex.getSuppressed()));
            assertThat(allExceptions)
                    .hasSize(targets.size())
                    .allSatisfy(exception -> {
                        assertThat(exception).isInstanceOfSatisfying(BlindRelationOverwriteException.class, e -> {
                            assertThat(ex.getAffectedRelation().getEntityName()).isEqualTo(ENTITY_B.getName());
                            assertThat(ex.getAffectedRelation().getRelationName()).isEqualTo(relation.getTargetEndPoint().getName());
                            assertThat(ex.getAffectedRelation().getEntityId()).isIn(targetIds);
                            assertThat(ex.getOriginalValue()).isEqualTo(originalSource);
                        });
                    });
        });

        // Also the succeeding target is not actually linked due to the exception throw earlier
        assertThat(queryEngine.isLinked(APPLICATION, newSourceRelationRequest, succeedingTarget.getEntityId(), PERMIT_ALWAYS)).isFalse();
    }

    @SpringBootApplication
    static class TestApplication {

        @Bean
        public DSLContextResolver autowiredDSLContextResolver(DSLContext dslContext) {
            return new AutowiredDSLContextResolver(dslContext);
        }

        @Bean
        public TableCreator jooqTableCreator(DSLContextResolver dslContextResolver) {
            return new JOOQTableCreator(dslContextResolver);
        }

        @Bean
        public QueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver, PlatformTransactionManager transactionManager) {
            return new TransactionalQueryEngine(
                    new JOOQQueryEngine(dslContextResolver, new JOOQTimedCountStrategy(Duration.ofMillis(500))),
                    transactionManager
            );
        }
    }
}
