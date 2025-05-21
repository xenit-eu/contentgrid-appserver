package com.contentgrid.appserver.query.engine.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.QueryEngine;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngineRollbackTest.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.Variable;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;

/**
 * Transaction rollback tests for JOOQQueryEngine.
 * Each test expects an exception being thrown by the JOOQQueryEngine and assert nothing has changed.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16:///",
        "logging.level.org.jooq.tools.LoggerListener=DEBUG"
})
@ContextConfiguration(classes = TestApplication.class)
public class JOOQQueryEngineRollbackTest {

    private static final SimpleAttribute PERSON_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final SimpleAttribute PERSON_VAT = SimpleAttribute.builder()
            .name(AttributeName.of("vat"))
            .column(ColumnName.of("vat"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .constraint(Constraint.unique())
            .build();

    private static final Entity PERSON = Entity.builder()
            .name(EntityName.of("person"))
            .table(TableName.of("person"))
            .pathSegment(PathSegmentName.of("persons"))
            .attribute(PERSON_NAME)
            .attribute(PERSON_VAT)
            .searchFilter(ExactSearchFilter.builder()
                    .attribute(PERSON_VAT)
                    .name(FilterName.of("vat"))
                    .build())
            .searchFilter(PrefixSearchFilter.builder()
                    .attribute(PERSON_NAME)
                    .name(FilterName.of("name~prefix"))
                    .build())
            .build();

    private static final SimpleAttribute INVOICE_NUMBER = SimpleAttribute.builder()
            .name(AttributeName.of("number"))
            .column(ColumnName.of("number"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .constraint(Constraint.unique())
            .build();

    private static final SimpleAttribute INVOICE_AMOUNT = SimpleAttribute.builder()
            .name(AttributeName.of("amount"))
            .column(ColumnName.of("amount"))
            .type(Type.DOUBLE)
            .constraint(Constraint.required())
            .build();

    private static final SimpleAttribute INVOICE_RECEIVED = SimpleAttribute.builder()
            .name(AttributeName.of("received"))
            .column(ColumnName.of("received"))
            .type(Type.DATETIME)
            .build();

    private static final SimpleAttribute INVOICE_PAY_BEFORE = SimpleAttribute.builder()
            .name(AttributeName.of("pay_before"))
            .column(ColumnName.of("pay_before"))
            .type(Type.DATETIME)
            .build();

    private static final SimpleAttribute INVOICE_IS_PAID = SimpleAttribute.builder()
            .name(AttributeName.of("is_paid"))
            .column(ColumnName.of("is_paid"))
            .type(Type.BOOLEAN)
            .build();

    private static final ContentAttribute INVOICE_CONTENT = ContentAttribute.builder()
            .name(AttributeName.of("content"))
            .pathSegment(PathSegmentName.of("content"))
            .idColumn(ColumnName.of("content__id"))
            .filenameColumn(ColumnName.of("content__filename"))
            .mimetypeColumn(ColumnName.of("content__mimetype"))
            .lengthColumn(ColumnName.of("content__length"))
            .build();

    private static final CompositeAttribute INVOICE_AUDIT_METADATA = CompositeAttributeImpl.builder()
            .name(AttributeName.of("audit_metadata"))
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("created_date"))
                    .column(ColumnName.of("audit_metadata__created_date"))
                    .type(Type.DATETIME)
                    .flag(CreatedDateFlag.builder().build())
                    .build())
            .attribute(UserAttribute.builder()
                    .name(AttributeName.of("created_by"))
                    .idColumn(ColumnName.of("audit_metadata__created_by_id"))
                    .namespaceColumn(ColumnName.of("audit_metadata__created_by_ns"))
                    .usernameColumn(ColumnName.of("audit_metadata__created_by_name"))
                    .flag(CreatorFlag.builder().build())
                    .build())
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("last_modified_date"))
                    .column(ColumnName.of("audit_metadata__last_modified_date"))
                    .type(Type.DATETIME)
                    .flag(ModifiedDateFlag.builder().build())
                    .build())
            .attribute(UserAttribute.builder()
                    .name(AttributeName.of("last_modified_by"))
                    .idColumn(ColumnName.of("audit_metadata__last_modified_by_id"))
                    .namespaceColumn(ColumnName.of("audit_metadata__last_modified_by_ns"))
                    .usernameColumn(ColumnName.of("audit_metadata__last_modified_by_name"))
                    .flag(ModifierFlag.builder().build())
                    .build())
            .build();

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .attribute(INVOICE_NUMBER)
            .attribute(INVOICE_AMOUNT)
            .attribute(INVOICE_RECEIVED)
            .attribute(INVOICE_PAY_BEFORE)
            .attribute(INVOICE_IS_PAID)
            .attribute(INVOICE_CONTENT)
            .attribute(INVOICE_AUDIT_METADATA)
            .searchFilter(ExactSearchFilter.builder()
                    .name(FilterName.of("number"))
                    .attribute(INVOICE_NUMBER)
                    .build())
            .build();

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
            .attribute(PRODUCT_CODE)
            .attribute(PRODUCT_DESCRIPTION)
            .searchFilter(ExactSearchFilter.builder()
                    .name(FilterName.of("code"))
                    .attribute(PRODUCT_CODE)
                    .build())
            .build();

    private static final ManyToOneRelation INVOICE_CUSTOMER = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
                    .required(true)
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON)
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .build())
            .targetReference(ColumnName.of("customer"))
            .build();

    private static final ManyToManyRelation PERSON_FRIENDS = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(PERSON)
                    .name(RelationName.of("friends"))
                    .pathSegment(PathSegmentName.of("friends"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON)
                    .build())
            .joinTable(TableName.of("person__friends"))
            .sourceReference(ColumnName.of("person_src_id"))
            .targetReference(ColumnName.of("person_tgt_id"))
            .build();

    private static final OneToOneRelation INVOICE_PREVIOUS = SourceOneToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("previous_invoice"))
                    .pathSegment(PathSegmentName.of("previous-invoice"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("next_invoice"))
                    .pathSegment(PathSegmentName.of("next-invoice"))
                    .build())
            .targetReference(ColumnName.of("previous_invoice"))
            .build();

    private static final ManyToManyRelation INVOICE_PRODUCTS = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("products"))
                    .pathSegment(PathSegmentName.of("products"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PRODUCT)
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .build())
            .joinTable(TableName.of("invoice__products"))
            .sourceReference(ColumnName.of("invoice_id"))
            .targetReference(ColumnName.of("product_id"))
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("invoicing-application"))
            .entity(INVOICE)
            .entity(PERSON)
            .entity(PRODUCT)
            .relation(INVOICE_CUSTOMER)
            .relation(INVOICE_PREVIOUS)
            .relation(PERSON_FRIENDS)
            .relation(INVOICE_PRODUCTS)
            .build();

    private static final TimeBasedEpochRandomGenerator UUID_GENERATOR = Generators.timeBasedEpochRandomGenerator();

    private static final EntityId ALICE_ID = EntityId.of(UUID_GENERATOR.generate());
    private static final EntityId BOB_ID = EntityId.of(UUID_GENERATOR.generate());
    private static final EntityId JOHN_ID = EntityId.of(UUID_GENERATOR.generate());
    private static final EntityId INVOICE1_ID = EntityId.of(UUID_GENERATOR.generate());
    private static final EntityId INVOICE2_ID = EntityId.of(UUID_GENERATOR.generate());
    private static final EntityId PRODUCT1_ID = EntityId.of(UUID_GENERATOR.generate());
    private static final EntityId PRODUCT2_ID = EntityId.of(UUID_GENERATOR.generate());
    private static final EntityId PRODUCT3_ID = EntityId.of(UUID_GENERATOR.generate());

    private static final Variable ENTITY_VAR = Variable.named("entity");

    private static boolean tablesCreated = false;

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private TableCreator tableCreator;

    @Autowired
    private QueryEngine queryEngine;

    @BeforeEach
    void setup() {
        if (!tablesCreated) {
            // Only create tables and insert data once, all tests should rollback automatically to this state
            tableCreator.createTables(APPLICATION);
            insertData();
            tablesCreated = true;
        }
    }

    void insertData() {
        var now = Instant.now();
        dslContext.insertInto(DSL.table("person"),
                        DSL.field("id", UUID.class), DSL.field("name", String.class), DSL.field("vat", String.class))
                .values(ALICE_ID.getValue(), "alice", "vat_1")
                .values(BOB_ID.getValue(), "bob", "vat_2")
                .values(JOHN_ID.getValue(), "john", "vat_3")
                .execute();
        dslContext.insertInto(DSL.table("invoice"))
                .set(DSL.field("id", UUID.class), INVOICE1_ID.getValue())
                .set(DSL.field("number", String.class), "invoice_1")
                .set(DSL.field("amount", Double.class), 10.0)
                .set(DSL.field("received", Instant.class), Instant.parse("2025-01-01T00:00:00Z"))
                .set(DSL.field("pay_before", Instant.class), Instant.parse("2025-01-31T23:59:59Z"))
                .set(DSL.field("is_paid", Boolean.class), true)
                .set(DSL.field("content__id", String.class), "content_1")
                .set(DSL.field("content__filename", String.class), "file.pdf")
                .set(DSL.field("content__mimetype", String.class), "application/pdf")
                .set(DSL.field("content__length", Long.class), 100L)
                .set(DSL.field("audit_metadata__created_date", Instant.class), now)
                .set(DSL.field("audit_metadata__created_by_name", String.class), "bob")
                .set(DSL.field("audit_metadata__last_modified_date", Instant.class), now)
                .set(DSL.field("audit_metadata__last_modified_by_name", String.class), "bob")
                .set(DSL.field("customer", UUID.class), ALICE_ID.getValue())
                .execute();
        dslContext.insertInto(DSL.table("invoice"))
                .set(DSL.field("id", UUID.class), INVOICE2_ID.getValue())
                .set(DSL.field("number", String.class), "invoice_2")
                .set(DSL.field("amount", Double.class), 20.0)
                .set(DSL.field("received", Instant.class), Instant.parse("2025-02-01T00:00:00Z"))
                .set(DSL.field("pay_before", Instant.class), Instant.parse("2025-02-28T23:59:59Z"))
                .set(DSL.field("is_paid", Boolean.class), false)
                // no content
                .set(DSL.field("audit_metadata__created_date", Instant.class), now)
                .set(DSL.field("audit_metadata__created_by_name", String.class), "alice")
                .set(DSL.field("audit_metadata__last_modified_date", Instant.class), now)
                .set(DSL.field("audit_metadata__last_modified_by_name", String.class), "alice")
                .set(DSL.field("customer", UUID.class), BOB_ID.getValue())
                .set(DSL.field("previous_invoice", UUID.class), INVOICE1_ID.getValue())
                .execute();
        dslContext.insertInto(DSL.table("person__friends"))
                .set(DSL.field("person_src_id", UUID.class), BOB_ID.getValue())
                .set(DSL.field("person_tgt_id", UUID.class), ALICE_ID.getValue())
                .execute();
        dslContext.insertInto(DSL.table("product"),
                        DSL.field("id", UUID.class), DSL.field("code", String.class), DSL.field("description", String.class))
                .values(PRODUCT1_ID.getValue(), "code_1", "test description")
                .values(PRODUCT2_ID.getValue(), "code_2", "")
                .values(PRODUCT3_ID.getValue(), "code_3", null)
                .execute();
        dslContext.insertInto(DSL.table("invoice__products"),
                        DSL.field("invoice_id", UUID.class), DSL.field("product_id", UUID.class))
                .values(INVOICE1_ID.getValue(), PRODUCT1_ID.getValue())
                .values(INVOICE1_ID.getValue(), PRODUCT2_ID.getValue())
                .execute();
    }

    void assertEntitiesUnchanged(Entity entity, List<EntityId> expected) {
        var results = queryEngine.findAll(APPLICATION, entity, Scalar.of(true), null);
        var resultList = results.getEntities().stream().map(EntityData::getId).toList();
        assertEquals(expected.size(), resultList.size());
        assertTrue(resultList.containsAll(expected));
    }

    void assertNothingChanged() {
        assertEntitiesUnchanged(PERSON, List.of(ALICE_ID, BOB_ID, JOHN_ID));
        assertEntitiesUnchanged(INVOICE, List.of(INVOICE1_ID, INVOICE2_ID));
        assertEntitiesUnchanged(PRODUCT, List.of(PRODUCT1_ID, PRODUCT2_ID, PRODUCT3_ID));

        assertTrue(queryEngine.isLinked(APPLICATION, PERSON_FRIENDS, BOB_ID, ALICE_ID));
        assertTrue(queryEngine.isLinked(APPLICATION, INVOICE_CUSTOMER, INVOICE1_ID, ALICE_ID));
        assertTrue(queryEngine.isLinked(APPLICATION, INVOICE_CUSTOMER, INVOICE2_ID, BOB_ID));
        assertTrue(queryEngine.isLinked(APPLICATION, INVOICE_PREVIOUS, INVOICE2_ID, INVOICE1_ID));
        assertTrue(queryEngine.isLinked(APPLICATION, INVOICE_PRODUCTS, INVOICE1_ID, PRODUCT1_ID));
        assertTrue(queryEngine.isLinked(APPLICATION, INVOICE_PRODUCTS, INVOICE1_ID, PRODUCT2_ID));

        // These are tried in invalidSetRelationData and invalidAddRelationData
        assertFalse(queryEngine.isLinked(APPLICATION, INVOICE_PREVIOUS, INVOICE1_ID, INVOICE1_ID));
        assertFalse(queryEngine.isLinked(APPLICATION, INVOICE_PREVIOUS, INVOICE1_ID, INVOICE2_ID));
        assertFalse(queryEngine.isLinked(APPLICATION, INVOICE_PRODUCTS, INVOICE1_ID, PRODUCT3_ID));
    }

    static Stream<Arguments> invalidCreateData() {
        return Stream.of(
                // Invalid entity name
                Arguments.of(EntityData.builder()
                        .name(EntityName.of("invalid_entity"))
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(), List.of()),
                // Invalid attribute name
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(AttributeName.of("invalid_attribute"))
                                .value("value")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(), List.of()),
                // Primary key provided
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .id(EntityId.of(UUID_GENERATOR.generate()))
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(), List.of()),
                // Primary key provided as attribute
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(UUID_GENERATOR.generate())
                                .build())
                        .build(), List.of()),
                // Missing required attribute
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(), List.of()),
                // Null for required value
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value(null)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(), List.of()),
                // Duplicate unique attribute
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("vat_1") // vat of alice
                                .build())
                        .build(), List.of()),
                // CompositeAttributeData instead of SimpleAttributeData
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(CompositeAttributeData.builder()
                                .name(PERSON_NAME.getName()) // no attributes
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(), List.of()),
                // Extra CompositeAttributeData attribute
                Arguments.of(EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .attribute(CompositeAttributeData.builder()
                                .name(AttributeName.of("invalid_attribute")) // no sub-attributes
                                .build())
                        .build(), List.of()),
                // Value of invalid type
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_IS_PAID.getName())
                                        .value("invalid_boolean") // String instead of boolean
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build()
                        )),
                // TODO: ACC-2051: provide audit_metadata/readonly attribute
                // Missing required relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of()), // customer is required
                // Wrong entity in relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToOneRelationData.builder()
                                        .entity(PERSON.getName()) // Should be invoice entity
                                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                                        .ref(INVOICE2_ID)
                                        .build()
                        )),
                // Non-existing relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToManyRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName()) // person -> invoices
                                        .ref(INVOICE2_ID)
                                        .build()
                        )),
                // Non-existing target in owning *-to-one relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                                        .ref(ALICE_ID) // Should be id of invoice
                                        .build()
                        )),
                // Non-existing target in non-owning *-to-one relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PREVIOUS.getTargetEndPoint().getName())
                                        .ref(ALICE_ID) // Should be id of invoice
                                        .build()
                        )),
                // Non-existing target in *-to-many relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToManyRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                                        .ref(PRODUCT3_ID)
                                        .ref(ALICE_ID) // should be id of product
                                        .build()
                        )),
                // XToManyRelationData for *-to-one relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToManyRelationData.builder() // should be XToOneRelationData
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                                        .ref(INVOICE2_ID)
                                        .build()
                        )),
                // XToOneRelationData for *-to-many relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToOneRelationData.builder() // should be XToManyRelationData
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                                        .ref(PRODUCT3_ID)
                                        .build()
                        )),
                // Duplicate value in one-to-one relation
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                                        .ref(INVOICE1_ID) // is already linked with INVOICE2_ID
                                        .build()
                        )),
                // Duplicate relation provided
                Arguments.of(
                        EntityData.builder()
                                .name(INVOICE.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_NUMBER.getName())
                                        .value("random_number")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_AMOUNT.getName())
                                        .value(BigDecimal.valueOf(25.0))
                                        .build())
                                .build(),
                        List.of(
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(ALICE_ID)
                                        .build(),
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName())
                                        .ref(BOB_ID)
                                        .build()
                        ))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCreateData")
    void createEntityInvalidData(EntityData data, List<RelationData> relations) {
        assertThrows(QueryEngineException.class, () -> queryEngine.create(APPLICATION, data, relations));
        assertNothingChanged();
    }

    static Stream<EntityData> invalidUpdateData() {
        return Stream.of(
                // Invalid entity name
                EntityData.builder()
                        .name(EntityName.of("invalid_entity"))
                        .id(BOB_ID)
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Invalid attribute name
                EntityData.builder()
                        .name(PERSON.getName())
                        .id(BOB_ID)
                        .attribute(SimpleAttributeData.builder()
                                .name(AttributeName.of("invalid_attribute"))
                                .value("value")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Unknown primary key provided
                EntityData.builder()
                        .name(PERSON.getName())
                        .id(EntityId.of(UUID_GENERATOR.generate()))
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // No primary key provided
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Primary key provided as attribute
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID.getValue())
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Null for required attribute
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value(null)
                                .build())
                        .build(),
                // Duplicate unique attribute
                EntityData.builder()
                        .name(PERSON.getName())
                        .id(BOB_ID)
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("vat_1") // vat of alice
                                .build())
                        .build(),
                // CompositeAttributeData instead of SimpleAttributeData
                EntityData.builder()
                        .name(PERSON.getName())
                        .id(BOB_ID)
                        .attribute(CompositeAttributeData.builder()
                                .name(PERSON_NAME.getName()) // no attributes
                                .build())
                        .build(),
                // Extra CompositeAttributeData attribute
                EntityData.builder()
                        .name(PERSON.getName())
                        .id(BOB_ID)
                        .attribute(CompositeAttributeData.builder()
                                .name(AttributeName.of("invalid_attribute")) // no sub-attributes
                                .build())
                        .build(),
                // Value of invalid type
                EntityData.builder()
                        .name(INVOICE.getName())
                        .id(INVOICE1_ID)
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_IS_PAID.getName())
                                .value("invalid_boolean") // String instead of boolean
                                .build())
                        .build(),
                // Empty data
                EntityData.builder()
                        .name(PERSON.getName())
                        .id(BOB_ID)
                        .build(),
                // No SimpleAttributeData provided
                EntityData.builder()
                        .name(INVOICE.getName())
                        .id(INVOICE1_ID)
                        .attribute(CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName()) // No attributes
                                .build())
                        .build()
                // TODO: ACC-2051: provide audit_metadata/readonly attribute
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateData")
    void updateEntityInvalidData(EntityData data) {
        assertThrows(QueryEngineException.class, () -> queryEngine.update(APPLICATION, data));
        assertNothingChanged();
    }

    static Stream<Arguments> invalidDeleteData() {
        return Stream.of(
                Arguments.of(INVOICE, ALICE_ID), // ALICE_ID is not an invoice
                Arguments.of(PERSON, ALICE_ID), // ALICE_ID is present in required relation customer
                Arguments.of(INVOICE, INVOICE1_ID), // INVOICE1_ID is still present in join-table of relation products
                Arguments.of(PRODUCT, PRODUCT1_ID) // PRODUCT1_ID is still present in join-table of relation invoices
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDeleteData")
    void deleteEntityInvalidId(Entity entity, EntityId id) {
        assertThrows(QueryEngineException.class, () -> queryEngine.delete(APPLICATION, entity, id));
        assertNothingChanged();
    }

    static Stream<Entity> invalidDeleteAllData() {
        // each table contains entities that are still referenced by other tables
        return Stream.of(PERSON, INVOICE, PRODUCT);
    }

    @ParameterizedTest
    @MethodSource("invalidDeleteAllData")
    void deleteAllInvalidEntity(Entity entity) {
        // persons are present in required relation customer
        assertThrows(QueryEngineException.class, () -> queryEngine.deleteAll(APPLICATION, entity));
        assertNothingChanged();
    }

    static Stream<Arguments> invalidSetRelationData() {
        return Stream.of(
                // Non-existing relation
                Arguments.of(ALICE_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName()) // invoices
                        .ref(INVOICE1_ID)
                        .build()),
                // Non-existing source id
                Arguments.of(ALICE_ID, XToOneRelationData.builder() // alice is not an invoice
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                        .ref(INVOICE2_ID)
                        .build()),
                // Non-existing target in owning *-to-one relation
                Arguments.of(INVOICE1_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName()) // previous_invoice
                        .ref(ALICE_ID) // alice is not an invoice
                        .build()),
                // Non-existing target in non-owning *-to-one relation
                Arguments.of(INVOICE2_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getTargetEndPoint().getName()) // next_invoice
                        .ref(ALICE_ID) // alice is not an invoice
                        .build()),
                // Duplicate value for a one-to-one relation
                Arguments.of(INVOICE1_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName()) // previous_invoice
                        .ref(INVOICE1_ID) // previous_invoice of INVOICE2_ID already contains INVOICE1_ID
                        .build()),
                // XToOneRelationData for *-to-many relation
                Arguments.of(INVOICE1_ID, XToOneRelationData.builder() // should be XToManyRelationData
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(PRODUCT3_ID)
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidSetRelationData")
    void setRelationInvalidData(EntityId id, XToOneRelationData relationData) {
        assertThrows(QueryEngineException.class, () -> queryEngine.setLink(APPLICATION, relationData, id));
        assertNothingChanged();
    }

    static Stream<Arguments> invalidUnsetRelationData() {
        return Stream.of(
                // Non-existing relation
                Arguments.of(INVOICE2_ID, ManyToOneRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name(RelationName.of("supplier"))
                                .pathSegment(PathSegmentName.of("supplier"))
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(PERSON)
                                .name(RelationName.of("non_existing"))
                                .pathSegment(PathSegmentName.of("non-existing"))
                                .build())
                        .targetReference(ColumnName.of("customer")) // Sneaky provide existing column
                        .build()),
                // Non-existing source id of an owning *-to-one relation
                Arguments.of(ALICE_ID, INVOICE_PREVIOUS),
                // Non-existing source id of a non-owning *-to-one relation
                Arguments.of(ALICE_ID, INVOICE_PREVIOUS.inverse()),
                // Non-existing source id of a *-to-many relation
                Arguments.of(ALICE_ID, INVOICE_PRODUCTS),
                // Required *-to-one relation
                Arguments.of(INVOICE1_ID, INVOICE_CUSTOMER),
                // Inverse required one-to-* relation
                Arguments.of(ALICE_ID, INVOICE_CUSTOMER.inverse())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUnsetRelationData")
    void unsetRelationInvalidData(EntityId id, Relation relation) {
        assertThrows(QueryEngineException.class, () -> queryEngine.unsetLink(APPLICATION, relation, id));
        assertNothingChanged();
    }

    static Stream<Arguments> invalidAddRelationData() {
        return Stream.of(
                // One-to-many: non-existing source ref
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName())
                        .ref(INVOICE2_ID)
                        .build()),
                // One-to-many: non-existing target ref
                Arguments.of(BOB_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName())
                        .ref(ALICE_ID) // should be an invoice
                        .build()),
                // Many-to-many: non-existing source ref
                Arguments.of(ALICE_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(PRODUCT1_ID)
                        .ref(PRODUCT3_ID)
                        .build()),
                // Many-to-many: non-existing target ref
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(ALICE_ID)
                        .ref(PRODUCT3_ID)
                        .build()),
                // Many-to-many: already linked values
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(PRODUCT1_ID)
                        .ref(PRODUCT3_ID)
                        .build()),
                // *-to-one relation
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                        .ref(INVOICE2_ID)
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidAddRelationData")
    void addRelationInvalidData(EntityId id, XToManyRelationData data) {
        assertThrows(QueryEngineException.class, () -> queryEngine.addLinks(APPLICATION, data, id));
        assertNothingChanged();
    }

    static Stream<Arguments> invalidRemoveRelationData() {
        return Stream.of(
                // Non-existing source ref
                Arguments.of(ALICE_ID, XToManyRelationData.builder() // not an invoice
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(PRODUCT1_ID)
                        .ref(PRODUCT3_ID)
                        .build()),
                // Non-existing target ref
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(ALICE_ID) // not a product
                        .ref(PRODUCT3_ID)
                        .build()),
                // Invalid target value
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(PRODUCT1_ID)
                        .ref(PRODUCT3_ID) // not linked with INVOICE1_ID
                        .build()),
                // Inverse of one-to-many required
                Arguments.of(ALICE_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName())
                        .ref(INVOICE1_ID)
                        .build()),
                // *-to-one relation
                Arguments.of(INVOICE2_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                        .ref(INVOICE1_ID)
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidRemoveRelationData")
    void removeRelationInvalidData(EntityId id, XToManyRelationData data) {
        assertThrows(QueryEngineException.class, () -> queryEngine.removeLinks(APPLICATION, data, id));
        assertNothingChanged();
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

        @Bean
        public QueryEngine jooqQueryEngine(DSLContextResolver dslContextResolver) {
            return new JOOQQueryEngine(dslContextResolver);
        }
    }
}
