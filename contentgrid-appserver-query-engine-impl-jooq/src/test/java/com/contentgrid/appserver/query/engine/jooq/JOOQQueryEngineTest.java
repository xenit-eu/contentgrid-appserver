package com.contentgrid.appserver.query.engine.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.RelationData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.data.XToManyRelationData;
import com.contentgrid.appserver.query.engine.api.data.XToOneRelationData;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.exception.QueryEngineException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringFunctionExpression;
import com.contentgrid.appserver.query.engine.jooq.JOOQQueryEngineTest.TestApplication;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import com.contentgrid.appserver.query.engine.jooq.resolver.DSLContextResolver;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.NumericFunction;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
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

    static Stream<ThunkExpression<Boolean>> validExpressions() {
        return Stream.of(
                // equals (double)
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                        Scalar.of(10.0)
                ),
                // equals (long)
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content"), SymbolicReference.path("length")),
                        Scalar.of(100L)
                ),
                // not equals
                Comparison.notEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                        Scalar.of("invoice_2")
                ),
                // and, less than, greater than
                LogicalOperation.conjunction(Stream.of(
                        Comparison.greater(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(0.0)
                        ),
                        Comparison.less(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(12.0)
                        )
                )),
                // and, less than or equals, greater than or equals
                LogicalOperation.conjunction(Stream.of(
                        Comparison.greaterOrEquals(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(0.0)
                        ),
                        Comparison.lessOrEquals(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(10.0)
                        )
                )),
                // or (when query parameter is provided multiple times)
                LogicalOperation.disjunction(Stream.of(
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(0.0)
                        ),
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(10.0)
                        )
                )),
                // not
                LogicalOperation.negation(
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                                Scalar.of("invoice_2")
                        )
                ),
                // plus
                Comparison.areEqual(
                        NumericFunction.plus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(10.0)),
                        Scalar.of(20.0)
                ),
                // multiply
                Comparison.areEqual(
                        NumericFunction.multiply(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(2L)),
                        Scalar.of(20.0)
                ),
                // minus
                Comparison.areEqual(
                        NumericFunction.minus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(10.0)),
                        Scalar.of(0.0)
                ),
                // divide
                Comparison.areEqual(
                        NumericFunction.divide(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(2L)),
                        Scalar.of(5.0)
                ),
                // modulo
                Comparison.areEqual(
                        NumericFunction.modulus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(3L)),
                        Scalar.of(1.0)
                ),
                // normalize
                StringComparison.normalizedEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                        Scalar.of("invoice_¹") // invoice_1
                ),
                // starts with
                StringComparison.startsWith(
                        StringFunctionExpression.normalize(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name"))),
                        StringFunctionExpression.normalize(Scalar.of("b")) // bob
                ),
                // contentgrid prefix search
                StringComparison.contentGridPrefixSearchMatch(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name")),
                        Scalar.of("Bö") // bob
                )
        );
    }

    @ParameterizedTest
    @MethodSource("validExpressions")
    void findAllValidExpression(ThunkExpression<Boolean> expression) {
        var slice = queryEngine.findAll(APPLICATION, INVOICE, expression, null);
        var results = slice.getEntities();

        assertEquals(1, results.size());
        var result = results.getFirst();
        var primaryKey = result.getId();
        assertEquals(INVOICE1_ID, primaryKey);
    }

    static Stream<ThunkExpression<Boolean>> invalidExpressions() {
        return Stream.of(
                // use of null value
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content"), SymbolicReference.path("id")),
                        Scalar.nullValue()
                ),
                // use of null string value
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content"), SymbolicReference.path("id")),
                        Scalar.of((String) null)
                ),
                // use of variable
                Comparison.areEqual(Variable.named("foo"), Scalar.of("alice")),
                // use of wrong variable
                Comparison.areEqual(
                        SymbolicReference.of(Variable.named("user"), SymbolicReference.path("number")),
                        Scalar.of("alice")
                ),
                // no path
                Comparison.areEqual(SymbolicReference.of(ENTITY_VAR), Scalar.of("alice")),
                // path too short
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content")),
                        Scalar.of("alice")
                ),
                // path too long
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number"), SymbolicReference.path("id")),
                        Scalar.of("alice")
                ),
                // non-existing attribute on entity
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("non_existing")),
                        Scalar.of("alice")
                ),
                // non-existing attribute on relation (exists on source entity)
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("number")),
                        Scalar.of("alice")
                ),
                // non-existing attribute on composite attribute (exists on parent attribute)
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("number")),
                        Scalar.of("alice")
                ),
                // variable access on entity (variable name from existing attribute)
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.pathVar("number")),
                        Scalar.of("alice")
                ),
                // variable access on composite attribute (variable name from existing attribute)
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.pathVar("name")),
                        Scalar.of("alice")
                ),
                // variable access on *-to-one relation
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.pathVar("__var_x0001__"), SymbolicReference.path("name")),
                        Scalar.of("alice")
                ),
                // no variable access on *-to-many relation
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.path("name")),
                        Scalar.of("alice")
                ),
                // same variable used multiple times
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.pathVar("x"), SymbolicReference.pathVar("name")),
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("previous_invoice"), SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.pathVar("x"), SymbolicReference.pathVar("name"))
                )
        );
    }

    @ParameterizedTest
    @MethodSource("invalidExpressions")
    void findAllInvalidExpression(ThunkExpression<Boolean> expression) {
        assertThrows(InvalidThunkExpressionException.class, () -> queryEngine.findAll(APPLICATION, INVOICE, expression, null));
    }

    static Stream<Arguments> validCreateData() {
        return Stream.of(
                // Valid person
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
                        .build(), List.of()),
                // All attributes and relations provided
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
                                // audit_metadata not provided (readonly)
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
                                        .ref(INVOICE2_ID)
                                        .build(),
                                XToOneRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PREVIOUS.getTargetEndPoint().getName()) // next_invoice
                                        .ref(INVOICE1_ID)
                                        .build(),
                                XToManyRelationData.builder()
                                        .entity(INVOICE.getName())
                                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                                        .ref(PRODUCT1_ID)
                                        .ref(PRODUCT2_ID)
                                        .build()
                        )),
                // Only required attributes/relations provided
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
                                        .build()
                        )),
                // Null for non-required attribute/relation
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
                                        .value(null) // null for attribute
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
                                        .ref(null) // null for owning *-to-one relation
                                        .build()
                                // TODO: null for non-owning *-to-one relation
                        )),
                // Empty CompositeAttributeData, empty XToManyRelationData
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
                                .attribute(CompositeAttributeData.builder()
                                        .name(INVOICE_CONTENT.getName())
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
                                        .build()
                        ))
        );
    }

    @ParameterizedTest
    @MethodSource("validCreateData")
    void createEntityValidData(EntityData data, List<RelationData> relations) {
        var id = queryEngine.create(APPLICATION, data, relations);
        var entity = APPLICATION.getEntityByName(data.getName()).orElseThrow();
        var actual = queryEngine.findById(APPLICATION, entity, id).orElseThrow();

        assertEntityDataEquals(data, actual);

        for (var relationData : relations) {
            var relation = APPLICATION.getRelationForEntity(entity, relationData.getName()).orElseThrow();
            switch (relationData) {
                case XToOneRelationData xToOneRelationData when xToOneRelationData.getRef() != null -> {
                    assertTrue(queryEngine.isLinked(APPLICATION, relation, id, xToOneRelationData.getRef()));
                }
                case XToManyRelationData xToManyRelationData -> {
                    for (var ref : xToManyRelationData.getRefs()) {
                        assertTrue(queryEngine.isLinked(APPLICATION, relation, id, ref));
                    }
                }
                default -> {
                    // Assert it is not linked
                    var link = queryEngine.findLink(APPLICATION, relation, id);
                    var xToOneRelationData = assertInstanceOf(XToOneRelationData.class, link);
                    assertNull(xToOneRelationData.getRef());
                }
            }
        }
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
                // Null for required relation
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
                                        .ref(null)
                                        .build()
                        )),
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
                        ))
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCreateData")
    void createEntityInvalidData(EntityData data, List<RelationData> relations) {
        assertThrows(QueryEngineException.class, () -> queryEngine.create(APPLICATION, data, relations));
    }

    static Stream<EntityData> validUpdateData() {
        return Stream.of(
                // Valid person
                EntityData.builder()
                        .name(PERSON.getName())
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
                // All attributes provided
                EntityData.builder()
                        .name(INVOICE.getName())
                        .id(INVOICE1_ID)
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
                        // audit_metadata not provided (readonly)
                        .build(),
                // Only one attribute provided
                EntityData.builder()
                        .name(INVOICE.getName())
                        .id(INVOICE1_ID)
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_AMOUNT.getName())
                                .value(BigDecimal.valueOf(25.0))
                                .build())
                        .build(),
                // Null for non-required attribute
                EntityData.builder()
                        .name(INVOICE.getName())
                        .id(INVOICE1_ID)
                        .attribute(SimpleAttributeData.builder()
                                .name(INVOICE_IS_PAID.getName())
                                .value(null)
                                .build())
                        .build(),
                // Update inside CompositeAttributeData
                EntityData.builder()
                        .name(INVOICE.getName())
                        .id(INVOICE1_ID)
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
        var id = data.getId();
        var actual = queryEngine.findById(APPLICATION, entity, id).orElseThrow();

        assertEntityDataEquals(data, actual);
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
    }

    static Stream<Arguments> validDeleteData() {
        return Stream.of(
                Arguments.of(PERSON, JOHN_ID),
                Arguments.of(INVOICE, INVOICE1_ID),
                Arguments.of(PRODUCT, PRODUCT1_ID)
        );
    }

    @ParameterizedTest
    @MethodSource("validDeleteData")
    void deleteEntityValidId(Entity entity, EntityId id) {
        queryEngine.delete(APPLICATION, entity, id);
        var maybeData = queryEngine.findById(APPLICATION, entity, id);
        assertTrue(maybeData.isEmpty());
    }

    static Stream<Arguments> invalidDeleteData() {
        return Stream.of(
                Arguments.of(INVOICE, ALICE_ID), // ALICE_ID is not an invoice
                Arguments.of(PERSON, ALICE_ID) // ALICE_ID is present in required relation customer
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDeleteData")
    void deleteEntityInvalidId(Entity entity, EntityId id) {
        assertThrows(QueryEngineException.class, () -> queryEngine.delete(APPLICATION, entity, id));
    }

    @Test
    void deleteAll() {
        queryEngine.deleteAll(APPLICATION, INVOICE);
        var slice = queryEngine.findAll(APPLICATION, INVOICE, Scalar.of(true), null);
        assertTrue(slice.getEntities().isEmpty());

        // now we can safely delete all persons (no invoices left with a required customer)
        queryEngine.deleteAll(APPLICATION, PERSON);
        slice = queryEngine.findAll(APPLICATION, PERSON, Scalar.of(true), null);
        assertTrue(slice.getEntities().isEmpty());
    }

    @Test
    void deleteAllInvalidEntity() {
        // persons are present in required relation customer
        assertThrows(QueryEngineException.class, () -> queryEngine.deleteAll(APPLICATION, PERSON));
    }

    static Stream<Arguments> validSetRelationData() {
        return Stream.of(
                // Owning one-to-one
                Arguments.of(INVOICE1_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName()) // previous_invoice
                        .ref(INVOICE2_ID)
                        .build()),
                // Non-owning one-to-one
                Arguments.of(INVOICE2_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getTargetEndPoint().getName()) // next_invoice
                        .ref(INVOICE1_ID)
                        .build()),
                // Many-to-one
                Arguments.of(INVOICE1_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName()) // customer
                        .ref(JOHN_ID) // originally ALICE_ID
                        .build()),
                // One-to-many
                Arguments.of(JOHN_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName()) // invoices
                        .ref(INVOICE1_ID)
                        .ref(INVOICE2_ID)
                        .build()),
                // Many-to-many
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName()) // products
                        .ref(PRODUCT2_ID) // originally PRODUCT1_ID and PRODUCT2_ID
                        .ref(PRODUCT3_ID)
                        .build()),
                // Null for *-to-one relation
                Arguments.of(INVOICE2_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName()) // previous_invoice
                        .ref(null)
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("validSetRelationData")
    void setRelationValidData(EntityId id, RelationData relationData) {
        queryEngine.setLink(APPLICATION, relationData, id);

        var relation = APPLICATION.getRelationForEntity(relationData.getEntity(), relationData.getName()).orElseThrow();
        switch (relationData) {
            case XToOneRelationData xToOneRelationData when xToOneRelationData.getRef() != null -> {
                assertTrue(queryEngine.isLinked(APPLICATION, relation, id, xToOneRelationData.getRef()));
            }
            case XToManyRelationData xToManyRelationData -> {
                for (var ref : xToManyRelationData.getRefs()) {
                    assertTrue(queryEngine.isLinked(APPLICATION, relation, id, ref));
                }
            }
            default -> {
                // Assert it is not linked
                var link = queryEngine.findLink(APPLICATION, relation, id);
                var xToOneRelationData = assertInstanceOf(XToOneRelationData.class, link);
                assertNull(xToOneRelationData.getRef());
            }
        }
    }

    static Stream<Arguments> invalidSetRelationData() {
        return Stream.of(
                // Non-existing relation
                Arguments.of(ALICE_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName()) // invoices
                        .ref(INVOICE1_ID)
                        .ref(INVOICE2_ID)
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
                // Non-existing target in *-to-many relation
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName()) // products
                        .ref(PRODUCT2_ID)
                        .ref(ALICE_ID) // alice is not a product
                        .ref(PRODUCT3_ID)
                        .build()),
                // Null for required relation
                Arguments.of(INVOICE1_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_CUSTOMER.getSourceEndPoint().getName()) // customer
                        .ref(null)
                        .build()),
                // Missing value that is required from other side
                Arguments.of(ALICE_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName()) // invoices
                        .ref(INVOICE2_ID) // customer of INVOICE1_ID will no longer contain ALICE_ID
                        .build()),
                // Duplicate value for a one-to-one relation
                Arguments.of(INVOICE1_ID, XToOneRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName()) // previous_invoice
                        .ref(INVOICE1_ID) // previous_invoice of INVOICE2_ID already contains INVOICE1_ID
                        .build()),
                // XToManyRelationData for *-to-one relation
                Arguments.of(INVOICE1_ID, XToManyRelationData.builder() // should be XToOneRelationData
                        .entity(INVOICE.getName())
                        .name(INVOICE_PREVIOUS.getSourceEndPoint().getName())
                        .ref(INVOICE2_ID)
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
    void setRelationInvalidData(EntityId id, RelationData relationData) {
        assertThrows(QueryEngineException.class, () -> queryEngine.setLink(APPLICATION, relationData, id));
    }

    static Stream<Arguments> validUnsetRelationData() {
        return Stream.of(
                // Non-empty owning *-to-one relation
                Arguments.of(INVOICE2_ID, INVOICE_PREVIOUS),
                // Empty owning *-to-one relation
                Arguments.of(INVOICE1_ID, INVOICE_PREVIOUS),
                // Non-empty non-owning *-to-one relation
                Arguments.of(INVOICE1_ID, INVOICE_PREVIOUS.inverse()),
                // Empty non-owning *-to-one relation
                Arguments.of(INVOICE2_ID, INVOICE_PREVIOUS.inverse()),
                // Non-empty *-to-many relation
                Arguments.of(INVOICE1_ID, INVOICE_PRODUCTS),
                // Empty *-to-many relation
                Arguments.of(JOHN_ID, INVOICE_CUSTOMER.inverse())
        );
    }

    @ParameterizedTest
    @MethodSource("validUnsetRelationData")
    void unsetRelationValidData(EntityId id, Relation relation) {
        queryEngine.unsetLink(APPLICATION, relation, id);

        var relationData = queryEngine.findLink(APPLICATION, relation, id);
        switch (relationData) {
            case XToOneRelationData xToOneRelationData -> assertNull(xToOneRelationData.getRef());
            case XToManyRelationData xToManyRelationData -> assertTrue(xToManyRelationData.getRefs().isEmpty());
        }
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
    }

    static Stream<Arguments> validAddRelationData() {
        return Stream.of(
                // One-to-many: new values
                Arguments.of(BOB_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName())
                        .ref(INVOICE1_ID)
                        .build()),
                // Many-to-many: new values
                Arguments.of(INVOICE2_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .ref(PRODUCT1_ID)
                        .ref(PRODUCT3_ID)
                        .build()),
                // One-to-many: already linked values
                Arguments.of(BOB_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName())
                        .ref(INVOICE2_ID)
                        .build()),
                // One-to-many: empty list
                Arguments.of(BOB_ID, XToManyRelationData.builder()
                        .entity(PERSON.getName())
                        .name(INVOICE_CUSTOMER.getTargetEndPoint().getName())
                        .build()),
                // Many-to-many: empty list
                Arguments.of(INVOICE2_ID, XToManyRelationData.builder()
                        .entity(INVOICE.getName())
                        .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                        .build())
        );
    }

    @ParameterizedTest
    @MethodSource("validAddRelationData")
    void addRelationValidData(EntityId id, XToManyRelationData data) {
        queryEngine.addLinks(APPLICATION, data, id);
        var relation = APPLICATION.getRelationForEntity(data.getEntity(), data.getName()).orElseThrow();

        for (var ref : data.getRefs()) {
            assertTrue(queryEngine.isLinked(APPLICATION, relation, id, ref));
        }
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
    }

    @Test
    void removeRelationValidData() {
        var data = XToManyRelationData.builder()
                .entity(INVOICE.getName())
                .name(INVOICE_PRODUCTS.getSourceEndPoint().getName())
                .ref(PRODUCT1_ID)
                .build();
        queryEngine.removeLinks(APPLICATION, data, INVOICE1_ID);

        var relation = APPLICATION.getRelationForEntity(data.getEntity(), data.getName()).orElseThrow();
        assertFalse(queryEngine.isLinked(APPLICATION, relation, INVOICE1_ID, PRODUCT1_ID));
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