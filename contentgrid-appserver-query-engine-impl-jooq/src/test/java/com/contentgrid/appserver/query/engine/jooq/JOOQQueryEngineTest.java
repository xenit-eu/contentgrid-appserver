package com.contentgrid.appserver.query.engine.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngineTest.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.Variable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///",
        "logging.level.org.jooq.tools.LoggerListener=DEBUG"
})
@ContextConfiguration(classes = {TestApplication.class})
@Transactional
class JOOQQueryEngineTest {

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
            .pathSegment(PathSegmentName.of("person"))
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
            .pathSegment(PathSegmentName.of("invoice"))
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

    private static final ManyToOneRelation INVOICE_CUSTOMER = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
//                    .required(true) // TODO: enable
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

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("invoicing-application"))
            .entity(INVOICE)
            .entity(PERSON)
            .relation(INVOICE_CUSTOMER)
            .relation(INVOICE_PREVIOUS)
            .relation(PERSON_FRIENDS)
            .build();

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();
    private static final UUID JOHN_ID = UUID.randomUUID();
    private static final UUID INVOICE1_ID = UUID.randomUUID();
    private static final UUID INVOICE2_ID = UUID.randomUUID();

    private static final Variable ENTITY_VAR = Variable.named("entity");

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private TableCreator tableCreator;

    @Autowired
    private QueryEngine queryEngine;

    @BeforeEach
    void setup() {
        // no AfterEach needed, because setup() is called in the same transaction of a test.
        createCGPrefixSearchNormalize();
        tableCreator.createTables(APPLICATION);
        insertData();
    }

    void createCGPrefixSearchNormalize() {
        var schema = DSL.schema("extensions");
        dslContext.createSchemaIfNotExists(schema).execute();
        dslContext.execute(DSL.sql("CREATE EXTENSION unaccent SCHEMA ?;", schema));
        dslContext.execute(DSL.sql("""
                CREATE OR REPLACE FUNCTION ?.contentgrid_prefix_search_normalize(arg text)
                  RETURNS text
                  LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
                RETURN ?.unaccent('extensions.unaccent', lower(normalize(arg, NFKC)));
                """, schema, schema));
    }

    void insertData() {
        var now = Instant.now();
        dslContext.insertInto(DSL.table("person"))
                .set(DSL.field("id", UUID.class), ALICE_ID)
                .set(DSL.field("name", String.class), "alice")
                .set(DSL.field("vat", String.class), "vat_1")
                .execute();
        dslContext.insertInto(DSL.table("person"))
                .set(DSL.field("id", UUID.class), BOB_ID)
                .set(DSL.field("name", String.class), "bob")
                .set(DSL.field("vat", String.class), "vat_2")
                .execute();
        dslContext.insertInto(DSL.table("person"))
                .set(DSL.field("id", UUID.class), JOHN_ID)
                .set(DSL.field("name", String.class), "john")
                .set(DSL.field("vat", String.class), "vat_3")
                .execute();
        dslContext.insertInto(DSL.table("invoice"))
                .set(DSL.field("id", UUID.class), INVOICE1_ID)
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
                .set(DSL.field("customer", UUID.class), ALICE_ID)
                .execute();
        dslContext.insertInto(DSL.table("invoice"))
                .set(DSL.field("id", UUID.class), INVOICE2_ID)
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
                .set(DSL.field("customer", UUID.class), BOB_ID)
                .set(DSL.field("previous_invoice", UUID.class), INVOICE1_ID)
                .execute();
        dslContext.insertInto(DSL.table("person__friends"))
                .set(DSL.field("person_src_id", UUID.class), BOB_ID)
                .set(DSL.field("person_tgt_id", UUID.class), ALICE_ID)
                .execute();
    }

    static Stream<EntityData> validCreateData() {
        return Stream.of(
                // valid person
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
                // all attributes provided
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
                                .name(INVOICE_RECEIVED.getName())
                                .value(Instant.parse("2025-01-01T00:00:00Z"))
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_PAY_BEFORE.getName())
                                .value(Instant.parse("2025-02-01T00:00:00Z"))
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_IS_PAID.getName())
                                .value(true)
                                .build())
                        .attribute(CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getId().getName())
                                        .value("random_id") // id comes from object storage
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getFilename().getName())
                                        .value("random_filename")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getMimetype().getName())
                                        .value("random_mimetype")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getLength().getName())
                                        .value(100L)
                                        .build())
                                .build())
                        // ACC-2051: audit_metadata not provided
                        .build(),
                // only required attributes provided
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
                // null for non-required attribute
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
                                .value(null)
                                .build())
                        .build(),
                // empty CompositeAttributeData
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
                        .attribute(CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .build())
                        .build()
        );
    }

    @ParameterizedTest
    @MethodSource("validCreateData")
    void createEntityValidData(EntityData data) {
        var id = queryEngine.create(APPLICATION, data, List.of());
        var entity = APPLICATION.getEntityByName(data.getName()).orElseThrow();
        var actual = queryEngine.findById(APPLICATION, entity, id).orElseThrow();

        assertEntityDataEquals(data, actual);
    }

    private static void assertEntityDataEquals(EntityData expected, EntityData actual) {
        // assert the expected data is present in the actual data
        for (var expectedAttr : expected.getAttributes()) {
            var actualAttr = actual.getAttributeByName(expectedAttr.getName()).orElseThrow();
            assertAttributeDataEquals(expectedAttr, actualAttr);
        }
    }

    private static void assertAttributeDataEquals(AttributeData expected, AttributeData actual) {
        // assert the expected data is present in the actual data
        switch (expected) {
            case SimpleAttributeData<?> expectedData -> {
                var actualData = assertInstanceOf(SimpleAttributeData.class, actual);
                assertEquals(expectedData.getValue(), actualData.getValue());
            }
            case CompositeAttributeData expectedData -> {
                var actualData = assertInstanceOf(CompositeAttributeData.class, actual);
                for (var expectedAttr : expectedData.getAttributes()) {
                    var actualAttr = actualData.getAttributeByName(expectedAttr.getName()).orElseThrow();
                    assertAttributeDataEquals(expectedAttr, actualAttr);
                }
            }
        }
    }

    static Stream<EntityData> invalidCreateData() {
        return Stream.of(
                // Invalid entity name
                EntityData.builder()
                        .name(EntityName.of("invalid_entity"))
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
                        .attribute(SimpleAttributeData.builder()
                                .name(AttributeName.of("invalid_attribute"))
                                .value("value")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Primary key provided
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
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(UUID.randomUUID())
                                .build())
                        .build(),
                // Missing required attribute
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Null for required value
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value(null)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Duplicate unique attribute
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_NAME.getName())
                                .value("random_name")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("vat_1") // vat of alice
                                .build())
                        .build(),
                // CompositeAttributeData instead of SimpleAttributeData
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(CompositeAttributeData.builder()
                                .name(PERSON_NAME.getName()) // no attributes
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("random_vat")
                                .build())
                        .build(),
                // Extra CompositeAttributeData attribute
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
                        .attribute(CompositeAttributeData.builder()
                                .name(AttributeName.of("invalid_attribute")) // no sub-attributes
                                .build())
                        .build(),
                // Value of invalid type
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
                                .name(INVOICE_RECEIVED.getName())
                                .value(Instant.parse("2025-01-01T00:00:00Z"))
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_PAY_BEFORE.getName())
                                .value(Instant.parse("2025-02-01T00:00:00Z"))
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_IS_PAID.getName())
                                .value("invalid_boolean") // String instead of boolean
                                .build())
                        .build()
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCreateData")
    void createEntityInvalidData(EntityData data) {
        assertThrows(QueryEngineException.class, () -> queryEngine.create(APPLICATION, data, List.of()));
    }

    static Stream<EntityData> validUpdateData() {
        return Stream.of(
                // valid person
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
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
                // all attributes provided
                EntityData.builder()
                        .name(INVOICE.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE.getPrimaryKey().getName())
                                .value(INVOICE1_ID)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_NUMBER.getName())
                                .value("new_number")
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_AMOUNT.getName())
                                .value(BigDecimal.valueOf(25.0))
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_RECEIVED.getName())
                                .value(Instant.parse("2025-01-01T00:00:00Z"))
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_PAY_BEFORE.getName())
                                .value(Instant.parse("2025-02-01T00:00:00Z"))
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_IS_PAID.getName())
                                .value(true)
                                .build())
                        .attribute(CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getId().getName())
                                        .value("new_id") // id comes from object storage
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getFilename().getName())
                                        .value("new_filename")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getMimetype().getName())
                                        .value("new_mimetype")
                                        .build())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getLength().getName())
                                        .value(100L)
                                        .build())
                                .build())
                        // ACC-2051: audit_metadata not provided
                        .build(),
                // only one attribute provided
                EntityData.builder()
                        .name(INVOICE.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE.getPrimaryKey().getName())
                                .value(INVOICE1_ID)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_AMOUNT.getName())
                                .value(BigDecimal.valueOf(25.0))
                                .build())
                        .build(),
                // null for non-required attribute
                EntityData.builder()
                        .name(INVOICE.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE.getPrimaryKey().getName())
                                .value(INVOICE1_ID)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_IS_PAID.getName())
                                .value(null)
                                .build())
                        .build(),
                // update inside CompositeAttributeData
                EntityData.builder()
                        .name(INVOICE.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE.getPrimaryKey().getName())
                                .value(INVOICE1_ID)
                                .build())
                        .attribute(CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName())
                                .attribute(SimpleAttributeData.builder()
                                        .name(INVOICE_CONTENT.getFilename().getName())
                                        .value("new_filename")
                                        .build())
                                .build())
                        .build()
        );
    }

    @ParameterizedTest
    @MethodSource("validUpdateData")
    void updateEntityValidData(EntityData data) {
        queryEngine.update(APPLICATION, data);
        var entity = APPLICATION.getEntityByName(data.getName()).orElseThrow();
        var primaryKeyData = (SimpleAttributeData<?>) data.getAttributeByName(entity.getPrimaryKey().getName()).orElseThrow();
        var id = primaryKeyData.getValue();
        var actual = queryEngine.findById(APPLICATION, entity, id).orElseThrow();

        assertEntityDataEquals(data, actual);
    }

    static Stream<EntityData> invalidUpdateData() {
        return Stream.of(
                // Invalid entity name
                EntityData.builder()
                        .name(EntityName.of("invalid_entity"))
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
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
                // Invalid attribute name
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
                                .build())
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
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(UUID.randomUUID())
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
                // Null for primary key provided
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(null)
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
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON_VAT.getName())
                                .value("vat_1") // vat of alice
                                .build())
                        .build(),
                // CompositeAttributeData instead of SimpleAttributeData
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
                                .build())
                        .attribute(CompositeAttributeData.builder()
                                .name(PERSON_NAME.getName()) // no attributes
                                .build())
                        .build(),
                // Extra CompositeAttributeData attribute
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
                                .build())
                        .attribute(CompositeAttributeData.builder()
                                .name(AttributeName.of("invalid_attribute")) // no sub-attributes
                                .build())
                        .build(),
                // Value of invalid type
                EntityData.builder()
                        .name(INVOICE.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE.getPrimaryKey().getName())
                                .value(INVOICE1_ID)
                                .build())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_IS_PAID.getName())
                                .value("invalid_boolean") // String instead of boolean
                                .build())
                        .build(),
                // Empty data
                EntityData.builder()
                        .name(PERSON.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(PERSON.getPrimaryKey().getName())
                                .value(BOB_ID)
                                .build())
                        .build(),
                // No SimpleAttributeData provided
                EntityData.builder()
                        .name(INVOICE.getName())
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE.getPrimaryKey().getName())
                                .value(INVOICE1_ID)
                                .build())
                        .attribute(CompositeAttributeData.builder()
                                .name(INVOICE_CONTENT.getName()) // No attributes
                                .build())
                        .build()
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUpdateData")
    void updateEntityInvalidData(EntityData data) {
        assertThrows(QueryEngineException.class, () -> queryEngine.update(APPLICATION, data));
    }

    @Test
    void deleteEntity() {
        queryEngine.delete(APPLICATION, PERSON, JOHN_ID);
        var maybeBob = queryEngine.findById(APPLICATION, PERSON, JOHN_ID);
        assertTrue(maybeBob.isEmpty());
    }

    @Test
    void deleteEntityInvalidId() {
        assertThrows(QueryEngineException.class, () -> queryEngine.delete(APPLICATION, PERSON, UUID.randomUUID()));
    }

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(JOOQThunkExpressionVisitorTest.TestApplication.class, args);
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