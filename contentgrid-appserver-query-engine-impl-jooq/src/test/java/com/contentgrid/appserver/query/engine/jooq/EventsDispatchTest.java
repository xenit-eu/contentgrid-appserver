package com.contentgrid.appserver.query.engine.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.events.EventHandlers;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.jooq.BlindRelationOverwriteTest.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.count.JOOQTimedCountStrategy;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.appserver.domain.values.EntityRequest;
import java.time.Duration;
import java.util.Objects;
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
    EventHandlers eventHandlers;


    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);
        Mockito.reset(eventHandlers);
    }

    @AfterEach
    void cleanup() {
        tableCreator.dropTables(APPLICATION);
    }

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
                PERMIT_ALWAYS
        );
        Mockito.verify(eventHandlers).dispatchCreate(
                eq(APPLICATION),
                eq(ENTITY_A.getName()),
                argThat(entityData -> {
                    var attribute = entityData.getAttributeByName(ATTRIBUTE_A.getName());
                    if (attribute.isPresent() && attribute.get() instanceof SimpleAttributeData<?> attr) {
                        assertThat(attr.getValue()).isEqualTo(123L);
                        return true;
                    }
                    return false;
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
                PERMIT_ALWAYS
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
                PERMIT_ALWAYS
        );

        Mockito.verify(eventHandlers).dispatchUpdate(
                eq(APPLICATION),
                eq(ENTITY_A.getName()),
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
                PERMIT_ALWAYS
        );

        queryEngine.delete(
                APPLICATION,
                EntityRequest.forEntity(created.getName(), created.getId()),
                PERMIT_ALWAYS
        );

        Mockito.verify(eventHandlers).dispatchDelete(
                eq(APPLICATION),
                eq(ENTITY_A.getName()),
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
    void verifyCreate_unhappy() {
        Mockito.doThrow(new RuntimeException("event failed")).when(eventHandlers)
                .dispatchCreate(any(), any(), any());

        assertThatThrownBy(() -> queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS
        )).isInstanceOf(RuntimeException.class);

        // Transaction rolled back → it doesn't exist in db
        var count = queryEngine.count(APPLICATION, ENTITY_A, PERMIT_ALWAYS);
        assertThat(count.count()).isZero();
    }

    @Test
    void verifyUpdate_unhappy() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS
        );

        Mockito.doThrow(new RuntimeException("event failed")).when(eventHandlers)
                .dispatchUpdate(any(), any(), any(), any());

        assertThatThrownBy(() -> queryEngine.update(
                APPLICATION,
                EntityData.builder()
                        .name(ENTITY_A.getName())
                        .id(created.getId())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(456)
                                .build())
                        .build(),
                PERMIT_ALWAYS
        )).isInstanceOf(RuntimeException.class);

        // Transaction rolled back → it's not updated in db
        var existing = queryEngine.findById(APPLICATION, created.getIdentity().toRequest(), PERMIT_ALWAYS).orElseThrow();
        var attr = (SimpleAttributeData<?>) existing.getAttributeByName(ATTRIBUTE_A.getName()).orElseThrow();
        assertThat(attr.getValue()).isEqualTo(123L);
    }

    @Test
    void verifyDelete_unhappy() {
        var created = queryEngine.create(
                APPLICATION,
                EntityCreateData.builder()
                        .entityName(ENTITY_A.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(ATTRIBUTE_A.getName())
                                .value(123)
                                .build())
                        .build(),
                PERMIT_ALWAYS
        );

        Mockito.doThrow(new RuntimeException("event failed")).when(eventHandlers)
                .dispatchDelete(any(), any(), any());

        assertThatThrownBy(() -> queryEngine.delete(
                APPLICATION,
                EntityRequest.forEntity(created.getName(), created.getId()),
                PERMIT_ALWAYS
        )).isInstanceOf(RuntimeException.class);

        // Transaction rolled back → it's not deleted in db
        var stillThere = queryEngine.findById(APPLICATION, created.getIdentity().toRequest(), PERMIT_ALWAYS);
        assertThat(stillThere).isPresent();
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
            .build();
    private static final Entity ENTITY_A = Entity.builder()
            .name(EntityName.of("entityA"))
            .pathSegment(PathSegmentName.of("entity-a"))
            .linkName(LinkName.of("entity-a"))
            .table(TableName.of("entity_a"))
            .attribute(ATTRIBUTE_A)
            .attribute(ATTRIBUTE_B)
            .build();
    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("test"))
            .entity(ENTITY_A)
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
                PlatformTransactionManager transactionManager, EventHandlers eventHandlers) {
            return new TransactionalQueryEngine(
                    new JOOQQueryEngine(dslContextResolver, new JOOQTimedCountStrategy(Duration.ofMillis(500)), eventHandlers),
                    transactionManager
            );
        }
    }
}
