package com.contentgrid.appserver.query.engine.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.ArgumentMatchers.eq;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.domain.values.RelationRequest;
import com.contentgrid.appserver.query.engine.api.CreateEventConsumer;
import com.contentgrid.appserver.query.engine.api.DeleteEventConsumer;
import com.contentgrid.appserver.query.engine.api.LinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.UnlinkEventConsumer;
import com.contentgrid.appserver.query.engine.api.UpdateEventConsumer;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.jooq.BlindRelationOverwriteTest.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQTimedCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.Scalar;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///",
        "logging.level.org.jooq.tools.LoggerListener=DEBUG"
})
@ContextConfiguration(classes = {TestApplication.class})
public class EventsDispatchTest {
    public static final Scalar<Boolean> PERMIT_ALWAYS = Scalar.of(true);

    @Autowired
    private QueryEngine queryEngine;

    @Autowired
    private TableCreator tableCreator;

    @MockitoBean
    CreateEventConsumer createEventConsumer;

    @MockitoBean
    UpdateEventConsumer updateEventConsumer;

    @MockitoBean
    DeleteEventConsumer deleteEventConsumer;

    @MockitoBean
    LinkEventConsumer linkEventConsumer;

    @MockitoBean
    UnlinkEventConsumer unlinkEventConsumer;


    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
        Mockito.reset(createEventConsumer, updateEventConsumer, deleteEventConsumer,
                linkEventConsumer, unlinkEventConsumer);
    }

    @AfterEach
    void cleanup() {
        tableCreator.dropTables(APPLICATION);
    }

    private static class SomeEventFailureException extends RuntimeException {}

    @Test
    void verifyCreate_happy() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );
        Mockito.verify(createEventConsumer).onEntityCreate(
                eq(APPLICATION),
                assertArg(entityData -> {
                    var attribute = entityData.getAttributeByName(ATTRIBUTE_A.getName());
                    assertThat(attribute).get().isInstanceOfSatisfying(SimpleAttributeData.class, attr ->
                            assertThat(attr.getValue()).isEqualTo(123L)
                    );
                })
        );

        // Transaction completed → it exists in db
        var exists = queryEngine.findById(APPLICATION, created.getIdentity().toRequest(), PERMIT_ALWAYS);
        assertThat(exists).isPresent();
    }


    @Test
    void verifyUpdate_happy() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        queryEngine.update(
                APPLICATION,
                EntityData.builder()
                        .name(ENTITY_A.getName())
                        .id(created.getId())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(456)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                updateEventConsumer
        );

        Mockito.verify(updateEventConsumer).onEntityUpdate(
                eq(APPLICATION),
                argThat(oldData -> {
                    var attribute = oldData.getAttributeByName(ATTRIBUTE_A.getName());
                    if (attribute.isPresent() && attribute.get() instanceof SimpleAttributeData<?> attr) {
                        assertThat(attr.getValue()).isEqualTo(123L);
                        return true;
                    }
                    return false;
                }),
                argThat(newData -> {
                    var attribute = newData.getAttributeByName(ATTRIBUTE_A.getName());
                    if (attribute.isPresent() && attribute.get() instanceof SimpleAttributeData<?> attr) {
                        assertThat(attr.getValue()).isEqualTo(456L);
                        return true;
                    }
                    return false;
                })
        );

        // Transaction completed → it's updated in db
        var updated = queryEngine.findById(APPLICATION, created.getIdentity().toRequest(), PERMIT_ALWAYS).orElseThrow();
        var updatedAttr = (SimpleAttributeData<?>) updated.getAttributeByName(ATTRIBUTE_A.getName()).orElseThrow();
        assertThat(updatedAttr.getValue()).isEqualTo(456L);
    }

    @Test
    void verifyDelete_happy() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        queryEngine.delete(
                APPLICATION,
                EntityRequest.forEntity(created.getName(), created.getId()),
                PERMIT_ALWAYS,
                deleteEventConsumer
        );

        Mockito.verify(deleteEventConsumer).onEntityDelete(
                eq(APPLICATION),
                argThat(oldData -> {
                    var attribute = oldData.getAttributeByName(ATTRIBUTE_A.getName());
                    if (attribute.isPresent() && attribute.get() instanceof SimpleAttributeData<?> attr) {
                        assertThat(attr.getValue()).isEqualTo(123L);
                        return true;
                    }
                    return false;
                })
        );

        // Transaction completed → it's deleted in db
        var missing = queryEngine.findById(APPLICATION, created.getIdentity().toRequest(), PERMIT_ALWAYS);
        assertThat(missing).isEmpty();
    }

    @Test
    void verifyCreate_eventThrows() {
        Mockito.doThrow(new SomeEventFailureException()).when(createEventConsumer)
                .onEntityCreate(any(), any());

        var createPayload = EntityCreateData.builder()
                .entityName(ENTITY_A.getName())
                .attribute(SimpleAttributeData.builder()
                        .name(ATTRIBUTE_A.getName())
                        .value(123)
                        .build())
                .build();
        assertThatThrownBy(() -> queryEngine.create(
                APPLICATION,
                createPayload,
                PERMIT_ALWAYS,
                createEventConsumer
        )).isInstanceOf(SomeEventFailureException.class);

        // Transaction rolled back → it doesn't exist in db
        var count = queryEngine.count(APPLICATION, ENTITY_A, PERMIT_ALWAYS);
        assertThat(count.count()).isZero();
    }

    @Test
    void verifyCreate_dbThrows() {
        queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_B.getName())
                                .value(true)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        // unique constraint violation on attribute B
        var createPayload = EntityCreateData.builder()
                .entityName(ENTITY_A.getName())
                .attribute(SimpleAttributeData.builder()
                        .name(ATTRIBUTE_A.getName())
                        .value(456)
                        .build())
                .attribute(SimpleAttributeData.builder()
                        .name(ATTRIBUTE_B.getName())
                        .value(true)
                        .build())
                .build();
        assertThatThrownBy(() -> queryEngine.create(
                APPLICATION,
                createPayload,
                PERMIT_ALWAYS,
                createEventConsumer
        )).isInstanceOf(QueryEngineException.class);

        // Failure to create → no events sent
        Mockito.verify(createEventConsumer, Mockito.times(1)).onEntityCreate(any(), any());
    }

    @Test
    void verifyUpdate_eventThrows() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        Mockito.doThrow(new SomeEventFailureException()).when(updateEventConsumer)
                .onEntityUpdate(any(), any(), any());

        var updatePayload = EntityData.builder()
                .name(ENTITY_A.getName())
                .id(created.getId())
                .attribute(SimpleAttributeData.builder()
                        .name(ATTRIBUTE_A.getName())
                        .value(456)
                        .build())
                .build();
        assertThatThrownBy(() -> queryEngine.update(
                APPLICATION,
                updatePayload,
                PERMIT_ALWAYS,
                updateEventConsumer
        )).isInstanceOf(SomeEventFailureException.class);

        // Transaction rolled back → it's not updated in db
        var existing = queryEngine.findById(APPLICATION, created.getIdentity().toRequest(), PERMIT_ALWAYS).orElseThrow();
        var attr = (SimpleAttributeData<?>) existing.getAttributeByName(ATTRIBUTE_A.getName()).orElseThrow();
        assertThat(attr.getValue()).isEqualTo(123L);
    }

    @Test
    void verifyUpdate_dbThrows() {
        queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_B.getName())
                                .value(true)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var second = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(456)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_B.getName())
                                .value(false)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        // Update second entity to violate unique constraint
        var updatePayload = EntityData.builder()
                .name(ENTITY_A.getName())
                .id(second.getId())
                .attribute(SimpleAttributeData.builder()
                        .name(ATTRIBUTE_A.getName())
                        .value(456)
                        .build())
                .attribute(SimpleAttributeData.builder()
                        .name(ATTRIBUTE_B.getName())
                        .value(true)
                        .build())
                .build();
        assertThatThrownBy(() -> queryEngine.update(
                APPLICATION,
                updatePayload,
                PERMIT_ALWAYS,
                updateEventConsumer
        )).isInstanceOf(QueryEngineException.class);

        // Failure to update → no events sent
        Mockito.verify(updateEventConsumer, Mockito.never()).onEntityUpdate(any(), any(), any());
    }

    @Test
    void verifyDelete_eventThrows() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        Mockito.doThrow(new SomeEventFailureException()).when(deleteEventConsumer)
                .onEntityDelete(any(), any());

        assertThatThrownBy(() -> queryEngine.delete(
                APPLICATION,
                EntityRequest.forEntity(created.getName(), created.getId()),
                PERMIT_ALWAYS,
                deleteEventConsumer
        )).isInstanceOf(SomeEventFailureException.class);

        // Transaction rolled back → it's not deleted in db
        var stillThere = queryEngine.findById(APPLICATION, created.getIdentity().toRequest(), PERMIT_ALWAYS);
        assertThat(stillThere).isPresent();
    }

    @Test
    void verifySetLink_happy() {
        var createdA = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdB = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_B.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_C.getName())
                                .value("test")
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA.getId(), RELATION_A_TO_B.getSourceEndPoint().getName());
        queryEngine.setLink(APPLICATION, relation, createdB.getId(), PERMIT_ALWAYS, linkEventConsumer);

        Mockito.verify(linkEventConsumer).onLink(
                eq(APPLICATION),
                assertArg(oldData -> assertThat(oldData.getId()).isEqualTo(createdA.getId())),
                assertArg(newData -> assertThat(newData.getId()).isEqualTo(createdA.getId()))
        );

        // Transaction completed → link exists in db
        var target = queryEngine.findTarget(APPLICATION, relation, PERMIT_ALWAYS);
        assertThat(target).isPresent();
        assertThat(target.get().entityId()).isEqualTo(createdB.getId());
    }

    @Test
    void verifyUnsetLink_happy() {
        var createdA = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdB = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_B.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_C.getName())
                                .value("test")
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA.getId(), RELATION_A_TO_B.getSourceEndPoint().getName());
        queryEngine.setLink(APPLICATION, relation, createdB.getId(), PERMIT_ALWAYS, linkEventConsumer);

        queryEngine.unsetLink(APPLICATION, relation, PERMIT_ALWAYS, unlinkEventConsumer);

        Mockito.verify(unlinkEventConsumer).onUnlink(
                eq(APPLICATION),
                assertArg(oldData -> assertThat(oldData.getId()).isEqualTo(createdA.getId())),
                assertArg(newData -> assertThat(newData.getId()).isEqualTo(createdA.getId()))
        );

        // Transaction completed → link removed from db
        var target = queryEngine.findTarget(APPLICATION, relation, PERMIT_ALWAYS);
        assertThat(target).isEmpty();
    }

    @Test
    void verifyAddLinks_happy() {
        var createdA1 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdA2 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(456)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA1.getId(), RELATION_A_TO_A.getSourceEndPoint().getName());
        queryEngine.addLinks(APPLICATION, relation, Set.of(createdA2.getId()), PERMIT_ALWAYS, linkEventConsumer);

        Mockito.verify(linkEventConsumer).onLink(
                eq(APPLICATION),
                assertArg(oldData -> assertThat(oldData.getId()).isEqualTo(createdA1.getId())),
                assertArg(newData -> assertThat(newData.getId()).isEqualTo(createdA1.getId()))
        );

        // Transaction completed → link exists in db
        var isLinked = queryEngine.isLinked(APPLICATION, relation, createdA2.getId(), PERMIT_ALWAYS);
        assertThat(isLinked).isTrue();
    }

    @Test
    void verifyRemoveLinks_happy() {
        var createdA1 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdA2 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(456)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA1.getId(), RELATION_A_TO_A.getSourceEndPoint().getName());
        queryEngine.addLinks(APPLICATION, relation, Set.of(createdA2.getId()), PERMIT_ALWAYS, linkEventConsumer);

        queryEngine.removeLinks(APPLICATION, relation, Set.of(createdA2.getId()), PERMIT_ALWAYS, unlinkEventConsumer);

        Mockito.verify(unlinkEventConsumer).onUnlink(
                eq(APPLICATION),
                assertArg(oldData -> assertThat(oldData.getId()).isEqualTo(createdA1.getId())),
                assertArg(newData -> assertThat(newData.getId()).isEqualTo(createdA1.getId()))
        );

        // Transaction completed → link removed from db
        var isLinked = queryEngine.isLinked(APPLICATION, relation, createdA2.getId(), PERMIT_ALWAYS);
        assertThat(isLinked).isFalse();
    }

    @Test
    void verifySetLink_eventThrows() {
        var createdA = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdB = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_B.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_C.getName())
                                .value("test")
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        Mockito.doThrow(new SomeEventFailureException()).when(linkEventConsumer)
                .onLink(any(), any(), any());

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA.getId(), RELATION_A_TO_B.getSourceEndPoint().getName());
        assertThatThrownBy(() -> queryEngine.setLink(APPLICATION, relation, createdB.getId(), PERMIT_ALWAYS, linkEventConsumer))
                .isInstanceOf(SomeEventFailureException.class);

        // Transaction rolled back → link not set in db
        var target = queryEngine.findTarget(APPLICATION, relation, PERMIT_ALWAYS);
        assertThat(target).isEmpty();
    }

    @Test
    void verifySetLink_dbThrows() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var missing = EntityId.of(UUID.randomUUID());
        var relation = RelationRequest.forRelation(ENTITY_A.getName(), created.getId(), RELATION_A_TO_B.getSourceEndPoint().getName());
        assertThatThrownBy(() -> queryEngine.setLink(APPLICATION, relation, missing, PERMIT_ALWAYS, linkEventConsumer))
                .isInstanceOf(QueryEngineException.class);

        Mockito.verify(linkEventConsumer, Mockito.never()).onLink(any(), any(), any());
    }

    @Test
    void verifyUnsetLink_eventThrows() {
        var createdA = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdB = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_B.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_C.getName())
                                .value("test")
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA.getId(), RELATION_A_TO_B.getSourceEndPoint().getName());
        queryEngine.setLink(APPLICATION, relation, createdB.getId(), PERMIT_ALWAYS, linkEventConsumer);

        Mockito.doThrow(new SomeEventFailureException()).when(unlinkEventConsumer)
                .onUnlink(any(), any(), any());

        assertThatThrownBy(() -> queryEngine.unsetLink(APPLICATION, relation, PERMIT_ALWAYS, unlinkEventConsumer))
                .isInstanceOf(SomeEventFailureException.class);

        // Transaction rolled back → link still set in db
        var target = queryEngine.findTarget(APPLICATION, relation, PERMIT_ALWAYS);
        assertThat(target).isPresent();
        assertThat(target.get().entityId()).isEqualTo(createdB.getId());
    }

    @Test
    void verifyAddLinks_eventThrows() {
        var createdA1 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdA2 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(456)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        Mockito.doThrow(new SomeEventFailureException()).when(linkEventConsumer)
                .onLink(any(), any(), any());

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA1.getId(), RELATION_A_TO_A.getSourceEndPoint().getName());
        assertThatThrownBy(() -> queryEngine.addLinks(APPLICATION, relation, Set.of(createdA2.getId()), PERMIT_ALWAYS, linkEventConsumer))
                .isInstanceOf(SomeEventFailureException.class);

        // Transaction rolled back → link not added in db
        var isLinked = queryEngine.isLinked(APPLICATION, relation, createdA2.getId(), PERMIT_ALWAYS);
        assertThat(isLinked).isFalse();
    }

    @Test
    void verifyRemoveLinks_eventThrows() {
        var createdA1 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var createdA2 = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(456)
                                .build())
                        .build(),
                PERMIT_ALWAYS,
                createEventConsumer
        );

        var relation = RelationRequest.forRelation(ENTITY_A.getName(), createdA1.getId(), RELATION_A_TO_A.getSourceEndPoint().getName());
        queryEngine.addLinks(APPLICATION, relation, Set.of(createdA2.getId()), PERMIT_ALWAYS, linkEventConsumer);

        Mockito.doThrow(new SomeEventFailureException()).when(unlinkEventConsumer)
                .onUnlink(any(), any(), any());

        assertThatThrownBy(() -> queryEngine.removeLinks(APPLICATION, relation, Set.of(createdA2.getId()), PERMIT_ALWAYS, unlinkEventConsumer))
                .isInstanceOf(SomeEventFailureException.class);

        // Transaction rolled back → link still exists in db
        var isLinked = queryEngine.isLinked(APPLICATION, relation, createdA2.getId(), PERMIT_ALWAYS);
        assertThat(isLinked).isTrue();
    }




    private static final Attribute ATTRIBUTE_A = SimpleAttribute.builder()
            .name(AttributeName.of("attribute_a"))
            .column(ColumnName.of("attribute_a"))
            .type(Type.LONG)
            .build();
    private static final Attribute ATTRIBUTE_B = SimpleAttribute.builder()
            .name(AttributeName.of("attribute_b"))
            .column(ColumnName.of("attribute_b"))
            .type(Type.BOOLEAN)
            .constraint(Constraint.unique())
            .build();
    private static final Attribute ATTRIBUTE_C = SimpleAttribute.builder()
            .name(AttributeName.of("attribute_c"))
            .column(ColumnName.of("attribute_c"))
            .type(Type.TEXT)
            .build();
    private static final Entity ENTITY_A = Entity.builder()
            .name(EntityName.of("entityA"))
            .pathSegment(PathSegmentName.of("entity-a"))
            .linkName(LinkName.of("entity-a"))
            .table(TableName.of("entity_a"))
            .attribute(ATTRIBUTE_A)
            .attribute(ATTRIBUTE_B)
            .build();
    private static final Entity ENTITY_B = Entity.builder()
            .name(EntityName.of("entityB"))
            .pathSegment(PathSegmentName.of("entity-b"))
            .linkName(LinkName.of("entity-b"))
            .table(TableName.of("entity_b"))
            .attribute(ATTRIBUTE_C)
            .build();
    private static final ManyToOneRelation RELATION_A_TO_B = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_A.getName())
                    .name(RelationName.of("entityB"))
                    .linkName(LinkName.of("entity-b"))
                    .pathSegment(PathSegmentName.of("b"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_B.getName())
                    .build())
            .targetReference(ColumnName.of("entity_b_id"))
            .build();
    private static final ManyToManyRelation RELATION_A_TO_A = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_A.getName())
                    .name(RelationName.of("related"))
                    .linkName(LinkName.of("related"))
                    .pathSegment(PathSegmentName.of("related"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(ENTITY_A.getName())
                    .name(null)
                    .linkName(null)
                    .build())
            .joinTable(TableName.of("entity_a_related"))
            .sourceReference(ColumnName.of("source_id"))
            .targetReference(ColumnName.of("target_id"))
            .build();
    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("test"))
            .entity(ENTITY_A)
            .entity(ENTITY_B)
            .relation(RELATION_A_TO_B)
            .relation(RELATION_A_TO_A)
            .build();

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
        public QueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver,
                PlatformTransactionManager transactionManager) {
            return new TransactionalQueryEngine(
                    new JOOQQueryEngine(dslContextResolver, new JOOQTimedCountStrategy(Duration.ofMillis(500))),
                    transactionManager
            );
        }
    }
}
