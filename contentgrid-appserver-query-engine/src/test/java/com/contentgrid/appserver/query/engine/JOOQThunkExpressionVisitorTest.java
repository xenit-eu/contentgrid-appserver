package com.contentgrid.appserver.query.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.contentgrid.appserver.query.engine.JOOQThunkExpressionVisitor.JOOQContext;
import com.contentgrid.appserver.query.engine.JOOQThunkExpressionVisitorTest.TestApplication;
import com.contentgrid.appserver.query.engine.expression.StringComparison;
import com.contentgrid.appserver.query.engine.expression.StringFunctionExpression;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.NumericFunction;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.Condition;
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
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///",
        "logging.level.org.jooq.tools.LoggerListener=DEBUG"
})
@ContextConfiguration(classes = {TestApplication.class})
class JOOQThunkExpressionVisitorTest {

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

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("invoicing-application"))
            .entity(INVOICE)
            .entity(PERSON)
            .relation(INVOICE_CUSTOMER)
            .relation(INVOICE_PREVIOUS)
            .relation(PERSON_FRIENDS)
            .build();

    private static boolean tablesCreated;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();
    private static final UUID JOHN_ID = UUID.randomUUID();
    private static final UUID INVOICE1_ID = UUID.randomUUID();
    private static final UUID INVOICE2_ID = UUID.randomUUID();

    private static final Variable ENTITY_VAR = Variable.named("entity");

    @Autowired
    private DSLContext dslContext;

    private final JOOQTableCreator tableCreator = new JOOQTableCreator();

    private final JOOQThunkExpressionVisitor personVisitor = new JOOQThunkExpressionVisitor(new JoinCollection(TableName.of("person")));
    private final JOOQThunkExpressionVisitor invoiceVisitor = new JOOQThunkExpressionVisitor(new JoinCollection(TableName.of("invoice")));

    @BeforeEach
    void setup() {
        if (!tablesCreated) {
            createCGPrefixSearchNormalize();
            tableCreator.createTables(dslContext, APPLICATION);
            insertData();
            tablesCreated = true;
        }
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

    @Test
    void findAlice() {
        // entity.name = alice
        ThunkExpression<?> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var condition = expression.accept(personVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON));
        var results = dslContext.selectFrom(DSL.table("person").as("p0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(ALICE_ID, result.get("id"));
        assertEquals("alice", result.get("name"));
        assertEquals("vat_1", result.get("vat"));
    }

    @Test
    void findAliceWithPrefixSearch() {
        // cg_prefix_search_normalize(entity.name) starts with cg_prefix_search_normalize(ALI)
        ThunkExpression<?> expression = StringComparison.startsWith(
                StringFunctionExpression.contentGridPrefixSearchNormalize(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("name"))),
                StringFunctionExpression.contentGridPrefixSearchNormalize(Scalar.of("ALI"))
        );
        var condition = expression.accept(personVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON));
        var results = dslContext.selectFrom(DSL.table("person").as("p0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(ALICE_ID, result.get("id"));
        assertEquals("alice", result.get("name"));
        assertEquals("vat_1", result.get("vat"));
    }

    @Test
    void findInvoiceOfAlice() {
        // entity.customer.name = alice
        ThunkExpression<?> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var condition = expression.accept(invoiceVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE));
        var results = dslContext.selectFrom(DSL.table("invoice").as("i0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }

    @Test
    void findInvoiceCreatedByAlice() {
        // entity.audit_metadata.created_by.name = alice
        ThunkExpression<?> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var condition = expression.accept(invoiceVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE));
        var results = dslContext.selectFrom(DSL.table("invoice").as("i0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE2_ID, result.get("id"));
    }

    @Test
    void findFriendsOfAlice() {
        // entity.friends.[_].name = alice
        ThunkExpression<?> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("friends"), SymbolicReference.pathVar("__var_x0001__"), SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var condition = expression.accept(personVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON));
        var results = dslContext.selectFrom(DSL.table("person").as("p0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(BOB_ID, result.get("id"));
    }

    @Test
    void findInvoiceNextOfAlice() {
        // entity.previous_invoice.customer.name = alice
        ThunkExpression<?> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("previous_invoice"), SymbolicReference.path("customer"), SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var condition = expression.accept(invoiceVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE));
        var results = dslContext.selectFrom(DSL.table("invoice").as("i0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE2_ID, result.get("id"));
    }

    @Test
    void findInvoicePrevOfBob() {
        // entity.next_invoice.customer.name = bob
        ThunkExpression<?> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("next_invoice"), SymbolicReference.path("customer"), SymbolicReference.path("name")),
                Scalar.of("bob")
        );
        var condition = expression.accept(invoiceVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE));
        var results = dslContext.selectFrom(DSL.table("invoice").as("i0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }

    @Test
    void findInvoiceWith2relations() {
        // entity.customer.name = entity.next_invoice.audit_metadata.created_by.name
        ThunkExpression<?> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("name")),
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("next_invoice"), SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name"))
        );
        var condition = expression.accept(invoiceVisitor, new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE));
        var results = dslContext.selectFrom(DSL.table("invoice").as("i0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }

    static Stream<Arguments> allFunctions() {
        return Stream.of(
                // equals (double)
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                        Scalar.of(10.0)
                )),
                // equals (long)
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content"), SymbolicReference.path("length")),
                        Scalar.of(100L)
                )),
                // not equals
                Arguments.of(Comparison.notEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                        Scalar.of("invoice_2")
                )),
                // and, less than, greater than
                Arguments.of(LogicalOperation.conjunction(Stream.of(
                        Comparison.greater(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(0.0)
                        ),
                        Comparison.less(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(12.0)
                        )
                ))),
                // and, less than or equals, greater than or equals
                Arguments.of(LogicalOperation.conjunction(Stream.of(
                        Comparison.greaterOrEquals(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(0.0)
                        ),
                        Comparison.lessOrEquals(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(10.0)
                        )
                ))),
                // or (when query parameter is provided multiple times)
                Arguments.of(LogicalOperation.disjunction(Stream.of(
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(0.0)
                        ),
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(10.0)
                        )
                ))),
                // not
                Arguments.of(LogicalOperation.negation(
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                                Scalar.of("invoice_2")
                        )
                )),
                // plus
                Arguments.of(Comparison.areEqual(
                        NumericFunction.plus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(10.0)),
                        Scalar.of(20.0)
                )),
                // multiply
                Arguments.of(Comparison.areEqual(
                        NumericFunction.multiply(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(2L)),
                        Scalar.of(20.0)
                )),
                // minus
                Arguments.of(Comparison.areEqual(
                        NumericFunction.minus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(10.0)),
                        Scalar.of(0.0)
                )),
                // divide
                Arguments.of(Comparison.areEqual(
                        NumericFunction.divide(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(2L)),
                        Scalar.of(5.0)
                )),
                // modulo
                Arguments.of(Comparison.areEqual(
                        NumericFunction.modulus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(3L)),
                        Scalar.of(1.0)
                )),
                // normalize
                Arguments.of(Comparison.areEqual(
                        StringFunctionExpression.normalize(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number"))),
                        StringFunctionExpression.normalize(Scalar.of("invoice_¹")) // invoice_1
                )),
                // starts with
                Arguments.of(StringComparison.startsWith(
                        StringFunctionExpression.normalize(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name"))),
                        StringFunctionExpression.normalize(Scalar.of("b")) // bob
                )),
                // contentgrid prefix search
                Arguments.of(StringComparison.startsWith(
                        StringFunctionExpression.contentGridPrefixSearchNormalize(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name"))),
                        StringFunctionExpression.contentGridPrefixSearchNormalize(Scalar.of("Bö")) // bob
                ))
        );
    }

    @ParameterizedTest
    @MethodSource("allFunctions")
    void findInvoice1(ThunkExpression<?> expression) {
        var context = new JOOQContext(APPLICATION, INVOICE);
        var condition = expression.accept(invoiceVisitor, context);
        var results = dslContext.selectFrom(DSL.table("invoice").as("i0"))
                .where((Condition) condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }

    static Stream<Arguments> illegalExpressions() {
        return Stream.of(
                // use of null value
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content"), SymbolicReference.path("id")),
                        Scalar.nullValue()
                )),
                // use of null string value
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content"), SymbolicReference.path("id")),
                        Scalar.of((String) null)
                )),
                // use of variable
                Arguments.of(Comparison.areEqual(Variable.named("foo"), Scalar.of("alice"))),
                // use of wrong variable
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(Variable.named("user"), SymbolicReference.path("number")),
                        Scalar.of("alice")
                )),
                // no path
                Arguments.of(Comparison.areEqual(SymbolicReference.of(ENTITY_VAR), Scalar.of("alice"))),
                // path too short
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content")),
                        Scalar.of("alice")
                )),
                // path too long
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number"), SymbolicReference.path("id")),
                        Scalar.of("alice")
                )),
                // non-existing attribute on entity
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("non_existing")),
                        Scalar.of("alice")
                )),
                // non-existing attribute on relation (exists on source entity)
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("number")),
                        Scalar.of("alice")
                )),
                // non-existing attribute on composite attribute (exists on parent attribute)
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("number")),
                        Scalar.of("alice")
                )),
                // variable access on entity (variable name from existing attribute)
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.pathVar("number")),
                        Scalar.of("alice")
                )),
                // variable access on composite attribute (variable name from existing attribute)
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.pathVar("name")),
                        Scalar.of("alice")
                )),
                // variable access on *-to-one relation
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.pathVar("__var_x0001__"), SymbolicReference.path("name")),
                        Scalar.of("alice")
                )),
                // no variable access on *-to-many relation
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.path("name")),
                        Scalar.of("alice")
                )),
                // same variable used multiple times
                Arguments.of(Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.pathVar("x"), SymbolicReference.pathVar("name")),
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("previous_invoice"), SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.pathVar("x"), SymbolicReference.pathVar("name"))
                ))
        );
    }

    @ParameterizedTest
    @MethodSource("illegalExpressions")
    void findIllegalExpression(ThunkExpression<?> expression) {
        var context = new JOOQContext(APPLICATION, INVOICE);
        assertThrows(InvalidThunkExpressionException.class, () -> {
            expression.accept(invoiceVisitor, context);
        });
    }

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }
}