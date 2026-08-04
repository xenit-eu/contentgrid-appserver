package com.contentgrid.appserver.query.engine.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.application.model.settings.ApplicationSettings;
import com.contentgrid.appserver.application.model.settings.database.DatabaseSettings;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SchemaName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.domain.values.EntityRequest;
import com.contentgrid.appserver.query.engine.api.CreateEventConsumer;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.data.EntityCreateData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.PermissionDeniedException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.appserver.query.engine.jooq.test.JooqTest;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regression test for ACC-3112: when the application uses a non-public database schema
 * (configured via {@link DatabaseSettings}), the ABAC read predicate built by
 * {@code JOOQSymbolicReferenceResolver} for {@link JOOQQueryEngine#findById} renders a
 * correlated {@code EXISTS} subquery that references the outer table alias with a schema
 * qualifier (e.g. {@code "cgtest"."i0"."customer"}), which PostgreSQL rejects with
 * {@code "invalid reference to FROM-clause entry for table \"i0\""}.
 * <p>
 * As a result, fetching a single entity the user is not authorized to read returns
 * {@code 500 Internal Server Error} (a {@code DataAccessException}) instead of the expected
 * {@code 403 Forbidden} ({@link PermissionDeniedException}).
 * <p>
 * The existing {@link JOOQQueryEngineTest} runs against the default {@code public} schema, so
 * the schema prefix is never rendered and the bug cannot surface there.
 */
@JooqTest
class JOOQQueryEngineSchemaTest {

    private static final Variable ENTITY_VAR = Variable.named("entity");
    private static final ThunkExpression<Boolean> PERMIT_CUSTOMER_ALICE = StringComparison.normalizedEqual(
            SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("name")),
            Scalar.of("alice")
    );

    private static final SimpleAttribute PERSON_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final SimpleAttribute INVOICE_NUMBER = SimpleAttribute.builder()
            .name(AttributeName.of("number"))
            .column(ColumnName.of("number"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final SimpleAttribute INVOICE_AMOUNT = SimpleAttribute.builder()
            .name(AttributeName.of("amount"))
            .column(ColumnName.of("amount"))
            .type(Type.DOUBLE)
            .constraint(Constraint.required())
            .build();

    private static final Entity PERSON = Entity.builder()
            .name(EntityName.of("person"))
            .table(TableName.of("person"))
            .pathSegment(PathSegmentName.of("persons"))
            .linkName(LinkName.of("persons"))
            .attribute(PERSON_NAME)
            .build();

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .linkName(LinkName.of("invoices"))
            .attribute(INVOICE_NUMBER)
            .attribute(INVOICE_AMOUNT)
            .build();

    private static final ManyToOneRelation INVOICE_CUSTOMER = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE.getName())
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
                    .linkName(LinkName.of("customer"))
                    .flag(RequiredEndpointFlag.INSTANCE)
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON.getName())
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .linkName(LinkName.of("invoices"))
                    .build())
            .targetReference(ColumnName.of("customer"))
            .build();

    /**
     * Application backed by a non-public schema. This is what triggers
     * {@link com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver}
     * to add a schema {@code RenderMapping} to the jOOQ {@code DSLContext}.
     */
    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("schema-test-application"))
            .entity(INVOICE)
            .entity(PERSON)
            .relation(INVOICE_CUSTOMER)
            .settings(ApplicationSettings.builder()
                    .database(DatabaseSettings.builder()
                            .schema(SchemaName.of("cgtest"))
                            .build())
                    .build())
            .build();

    @Autowired
    private TableCreator tableCreator;

    @Autowired
    private QueryEngine queryEngine;

    @MockitoBean
    private CreateEventConsumer createEventConsumer;

    private EntityId aliceId;
    private EntityId bobId;
    private EntityId invoiceForAliceId;
    private EntityId invoiceForBobId;

    @BeforeEach
    void setup() {
        tableCreator.createTables(APPLICATION);

        var alice = queryEngine.create(APPLICATION, EntityCreateData.builder()
                .entityName(PERSON.getName())
                .attribute(SimpleAttributeData.builder().name(PERSON_NAME.getName()).value("alice").build())
                .build(), Scalar.of(true), createEventConsumer);
        aliceId = alice.getIdentity().getEntityId();

        var bob = queryEngine.create(APPLICATION, EntityCreateData.builder()
                .entityName(PERSON.getName())
                .attribute(SimpleAttributeData.builder().name(PERSON_NAME.getName()).value("bob").build())
                .build(), Scalar.of(true), createEventConsumer);
        bobId = bob.getIdentity().getEntityId();

        var invoiceForAlice = queryEngine.create(APPLICATION, EntityCreateData.builder()
                .entityName(INVOICE.getName())
                .attribute(SimpleAttributeData.builder().name(INVOICE_NUMBER.getName()).value("invoice_a").build())
                .attribute(SimpleAttributeData.builder().name(INVOICE_AMOUNT.getName()).value(BigDecimal.valueOf(10.0)).build())
                .relation(XToOneRelationData.builder().name(INVOICE_CUSTOMER.getSourceEndPoint().getName()).ref(aliceId).build())
                .build(), Scalar.of(true), createEventConsumer);
        invoiceForAliceId = invoiceForAlice.getIdentity().getEntityId();

        var invoiceForBob = queryEngine.create(APPLICATION, EntityCreateData.builder()
                .entityName(INVOICE.getName())
                .attribute(SimpleAttributeData.builder().name(INVOICE_NUMBER.getName()).value("invoice_b").build())
                .attribute(SimpleAttributeData.builder().name(INVOICE_AMOUNT.getName()).value(BigDecimal.valueOf(20.0)).build())
                .relation(XToOneRelationData.builder().name(INVOICE_CUSTOMER.getSourceEndPoint().getName()).ref(bobId).build())
                .build(), Scalar.of(true), createEventConsumer);
        invoiceForBobId = invoiceForBob.getIdentity().getEntityId();
    }

    @AfterEach
    void cleanup() {
        tableCreator.dropTables(APPLICATION);
    }

    @Test
    void findByIdWithRelationBasedPermissionAgainstNonPublicSchema() {
        // invoice whose customer is alice -> permitted, must be returned
        var permitted = queryEngine.findById(APPLICATION,
                EntityRequest.forEntity(INVOICE.getName(), invoiceForAliceId), PERMIT_CUSTOMER_ALICE);
        assertThat(permitted).isPresent();
        assertThat(permitted.get().getAttributeByName(INVOICE_NUMBER.getName()))
                .hasValueSatisfying(attr -> assertThat(((SimpleAttributeData<?>) attr).getValue()).isEqualTo("invoice_a"));

        // invoice whose customer is bob -> NOT permitted.
        // BUG (ACC-3112): this throws a jOOQ DataAccessException (SQL error
        // "invalid reference to FROM-clause entry for table i0") instead of PermissionDeniedException,
        // because the correlated EXISTS subquery renders the outer alias as "cgtest"."i0"."customer".
        var thrown = assertThrows(PermissionDeniedException.class, () -> queryEngine.findById(APPLICATION,
                EntityRequest.forEntity(INVOICE.getName(), invoiceForBobId), PERMIT_CUSTOMER_ALICE));
        assertThat(thrown.getEntityId()).isEqualTo(invoiceForBobId);
    }
}
