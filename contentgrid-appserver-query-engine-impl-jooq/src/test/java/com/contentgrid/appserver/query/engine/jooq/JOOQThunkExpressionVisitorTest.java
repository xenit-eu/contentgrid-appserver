package com.contentgrid.appserver.query.engine.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.relations.flags.RequiredEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.searchfilters.FullTextSearchAttributeSearchFilter;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.api.exception.InvalidThunkExpressionException;
import com.contentgrid.appserver.query.engine.api.thunx.expression.StringComparison;
import com.contentgrid.appserver.query.engine.jooq.test.JooqTest;
import com.contentgrid.thunx.predicates.model.CollectionValue;
import com.contentgrid.thunx.predicates.model.Comparison;
import com.contentgrid.thunx.predicates.model.ListValue;
import com.contentgrid.thunx.predicates.model.LogicalOperation;
import com.contentgrid.thunx.predicates.model.NumericFunction;
import com.contentgrid.thunx.predicates.model.Scalar;
import com.contentgrid.thunx.predicates.model.SetValue;
import com.contentgrid.thunx.predicates.model.SymbolicReference;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.predicates.model.Variable;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.Arguments.ArgumentSet;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@JooqTest
@Transactional
class JOOQThunkExpressionVisitorTest {

    private static final SimpleAttribute PERSON_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final SimpleAttribute ORDER_ORDER = SimpleAttribute.builder()
            .name(AttributeName.of("order"))
            .column(ColumnName.of("order"))
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

    private static final SimpleAttribute PERSON_COMMENT = SimpleAttribute.builder()
            .name(AttributeName.of("comment"))
            .column(ColumnName.of("comment"))
            .type(Type.TEXT)
            .build();

    private static final Entity PERSON = Entity.builder()
            .name(EntityName.of("person"))
            .table(TableName.of("person"))
            .pathSegment(PathSegmentName.of("persons"))
            .linkName(LinkName.of("persons"))
            .attribute(PERSON_NAME)
            .attribute(PERSON_VAT)
            .attribute(PERSON_COMMENT)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .attribute(PERSON_VAT)
                    .name(FilterName.of("vat"))
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.PREFIX)
                    .attribute(PERSON_NAME)
                    .name(FilterName.of("name~prefix"))
                    .build())
            .searchFilter(FullTextSearchAttributeSearchFilter.builder()
                    .attribute(PERSON_COMMENT)
                    .locale(Locale.ENGLISH)
                    .name(FilterName.of("comment~fts"))
                    .build())
            .build();

    private static final Entity FRENCH_PERSON = Entity.builder()
            .name(EntityName.of("french-person"))
            .table(TableName.of("french-person"))
            .pathSegment(PathSegmentName.of("french-persons"))
            .linkName(LinkName.of("french-persons"))
            .attribute(PERSON_NAME)
            .attribute(PERSON_VAT)
            .attribute(PERSON_COMMENT)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .attribute(PERSON_VAT)
                    .name(FilterName.of("vat"))
                    .build())
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.PREFIX)
                    .attribute(PERSON_NAME)
                    .name(FilterName.of("name~prefix"))
                    .build())
            .searchFilter(FullTextSearchAttributeSearchFilter.builder()
                    .attribute(PERSON_COMMENT)
                    .locale(Locale.FRENCH)
                    .name(FilterName.of("comment~fts"))
                    .build())
            .build();

    private static final Entity ORDER = Entity.builder()
            .name(EntityName.of("order"))
            .table(TableName.of("order"))
            .pathSegment(PathSegmentName.of("orders"))
            .linkName(LinkName.of("orders"))
            .attribute(ORDER_ORDER)
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
            .type(Type.DATE)
            .build();

    private static final SimpleAttribute INVOICE_PAY_BEFORE = SimpleAttribute.builder()
            .name(AttributeName.of("pay_before"))
            .column(ColumnName.of("pay_before"))
            .type(Type.DATE)
            .build();

    private static final SimpleAttribute INVOICE_PAY_TIMESTAMP = SimpleAttribute.builder()
            .name(AttributeName.of("pay_timestamp"))
            .column(ColumnName.of("pay_timestamp"))
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
            .linkName(LinkName.of("content"))
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
                    .flag(CreatedDateFlag.INSTANCE)
                    .build())
            .attribute(UserAttribute.builder()
                    .name(AttributeName.of("created_by"))
                    .idColumn(ColumnName.of("audit_metadata__created_by_id"))
                    .namespaceColumn(ColumnName.of("audit_metadata__created_by_ns"))
                    .usernameColumn(ColumnName.of("audit_metadata__created_by_name"))
                    .flag(CreatorFlag.INSTANCE)
                    .build())
            .attribute(SimpleAttribute.builder()
                    .name(AttributeName.of("last_modified_date"))
                    .column(ColumnName.of("audit_metadata__last_modified_date"))
                    .type(Type.DATETIME)
                    .flag(ModifiedDateFlag.INSTANCE)
                    .build())
            .attribute(UserAttribute.builder()
                    .name(AttributeName.of("last_modified_by"))
                    .idColumn(ColumnName.of("audit_metadata__last_modified_by_id"))
                    .namespaceColumn(ColumnName.of("audit_metadata__last_modified_by_ns"))
                    .usernameColumn(ColumnName.of("audit_metadata__last_modified_by_name"))
                    .flag(ModifierFlag.INSTANCE)
                    .build())
            .build();

    private static final SimpleAttribute INVOICE_VERSION = SimpleAttribute.builder()
            .name(AttributeName.of("version"))
            .column(ColumnName.of("version"))
            .type(Type.LONG)
            .flag(ETagFlag.INSTANCE)
            .build();

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .linkName(LinkName.of("invoices"))
            .attribute(INVOICE_VERSION)
            .attribute(INVOICE_NUMBER)
            .attribute(INVOICE_AMOUNT)
            .attribute(INVOICE_RECEIVED)
            .attribute(INVOICE_PAY_BEFORE)
            .attribute(INVOICE_PAY_TIMESTAMP)
            .attribute(INVOICE_IS_PAID)
            .attribute(INVOICE_CONTENT)
            .attribute(INVOICE_AUDIT_METADATA)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("number"))
                    .attribute(INVOICE_NUMBER)
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("invoice_num"))
                    .propertyPath(PropertyPath.of(INVOICE_NUMBER.getName()))
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("amount"))
                    .propertyPath(PropertyPath.of(INVOICE_AMOUNT.getName()))
                    .build())
            .sortableField(SortableField.builder()
                    .name(SortableName.of("content_length"))
                    .propertyPath(PropertyPath.of(INVOICE_CONTENT.getName(), AttributeName.of("length")))
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
            .linkName(LinkName.of("products"))
            .attribute(PRODUCT_CODE)
            .attribute(PRODUCT_DESCRIPTION)
            .searchFilter(AttributeSearchFilter.builder()
                    .operation(Operation.EXACT)
                    .name(FilterName.of("code"))
                    .attribute(PRODUCT_CODE)
                    .build())
            .build();

    private static final Entity ADDRESS = Entity.builder()
            .name(EntityName.of("address"))
            .table(TableName.of("address"))
            .pathSegment(PathSegmentName.of("addresses"))
            .linkName(LinkName.of("addresses"))
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

    private static final ManyToManyRelation PERSON_FRIENDS = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(PERSON.getName())
                    .name(RelationName.of("friends"))
                    .pathSegment(PathSegmentName.of("friends"))
                    .linkName(LinkName.of("friends"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PERSON.getName())
                    .name(RelationName.of("__internal_person_friends"))
                    .flag(HiddenEndpointFlag.INSTANCE)
                    .build())
            .joinTable(TableName.of("person__friends"))
            .sourceReference(ColumnName.of("person_src_id"))
            .targetReference(ColumnName.of("person_tgt_id"))
            .build();

    private static final OneToOneRelation INVOICE_PREVIOUS = SourceOneToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE.getName())
                    .name(RelationName.of("previous_invoice"))
                    .pathSegment(PathSegmentName.of("previous-invoice"))
                    .linkName(LinkName.of("previous_invoice"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE.getName())
                    .name(RelationName.of("next_invoice"))
                    .pathSegment(PathSegmentName.of("next-invoice"))
                    .linkName(LinkName.of("next_invoice"))
                    .build())
            .targetReference(ColumnName.of("previous_invoice"))
            .build();

    private static final ManyToManyRelation INVOICE_PRODUCTS = ManyToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE.getName())
                    .name(RelationName.of("products"))
                    .pathSegment(PathSegmentName.of("products"))
                    .linkName(LinkName.of("products"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(PRODUCT.getName())
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .linkName(LinkName.of("invoices"))
                    .build())
            .joinTable(TableName.of("invoice__products"))
            .sourceReference(ColumnName.of("invoice_id"))
            .targetReference(ColumnName.of("product_id"))
            .build();

    private static final OneToManyRelation PERSON_ADDRESSES = OneToManyRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(PERSON.getName())
                    .name(RelationName.of("addresses"))
                    .pathSegment(PathSegmentName.of("addresses"))
                    .linkName(LinkName.of("addresses"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(ADDRESS.getName())
                    .name(RelationName.of("person"))
                    .pathSegment(PathSegmentName.of("person"))
                    .linkName(LinkName.of("person"))
                    .build())
            .sourceReference(ColumnName.of("person_id"))
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("invoicing-application"))
            .entity(INVOICE)
            .entity(PERSON)
            .entity(FRENCH_PERSON)
            .entity(PRODUCT)
            .entity(ADDRESS)
            .entity(ORDER)
            .relation(INVOICE_CUSTOMER)
            .relation(INVOICE_PREVIOUS)
            .relation(PERSON_FRIENDS)
            .relation(INVOICE_PRODUCTS)
            .relation(PERSON_ADDRESSES)
            .build();

    private static final TimeBasedEpochRandomGenerator UUID_GENERATOR = Generators.timeBasedEpochRandomGenerator();

    private static final UUID ALICE_ID = UUID_GENERATOR.generate();
    private static final UUID BOB_ID = UUID_GENERATOR.generate();
    private static final UUID JOHN_ID = UUID_GENERATOR.generate();
    private static final UUID THIJS_ID = UUID_GENERATOR.generate();
    private static final UUID JACQUES_ID = UUID_GENERATOR.generate();
    private static final UUID INVOICE1_ID = UUID_GENERATOR.generate();
    private static final UUID INVOICE2_ID = UUID_GENERATOR.generate();
    private static final UUID INVOICE3_ID = UUID_GENERATOR.generate();
    private static final UUID PRODUCT1_ID = UUID_GENERATOR.generate();
    private static final UUID PRODUCT2_ID = UUID_GENERATOR.generate();
    private static final UUID PRODUCT3_ID = UUID_GENERATOR.generate();
    private static final UUID ADDRESS1_ID = UUID_GENERATOR.generate();
    private static final UUID ADDRESS2_ID = UUID_GENERATOR.generate();

    private static final Variable ENTITY_VAR = Variable.named("entity");

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private TableCreator tableCreator;

    private static final JOOQThunkExpressionVisitor VISITOR = new JOOQThunkExpressionVisitor();

    @BeforeEach
    void setup() {
        // no AfterEach needed, because setup() is called in the same transaction of a test.
        tableCreator.createTables(APPLICATION);
        insertData();
    }

    void insertData() {
        var now = Instant.now();
        dslContext.insertInto(DSL.table(DSL.name("person")),
                        DSL.field(DSL.name("id"), UUID.class),
                        DSL.field(DSL.name("name"), String.class),
                        DSL.field(DSL.name("vat"), String.class),
                        DSL.field(DSL.name("comment"), String.class))
                .values(ALICE_ID, "alice", "vat_1", "Comment with the words foo and bar.")
                .values(BOB_ID, "bob", "vat_2", "Another comment mentioning foo.")
                .values(JOHN_ID, "john", "vat_3", "Just a random comment.")
                .values(THIJS_ID, "Thĳs", "Thijs", "Comment with bar and foo, but also Thĳs.")
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("french-person")),
                        DSL.field(DSL.name("id"), UUID.class),
                        DSL.field(DSL.name("name"), String.class),
                        DSL.field(DSL.name("vat"), String.class),
                        DSL.field(DSL.name("comment"), String.class))
                .values(JACQUES_ID, "jacques", "vat_3", "Je ne suis pas une baguette.")
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("invoice")))
                .set(DSL.field(DSL.name("id"), UUID.class), INVOICE1_ID)
                .set(DSL.field(DSL.name("version"), Long.class), 150L)
                .set(DSL.field(DSL.name("number"), String.class), "invoice_1")
                .set(DSL.field(DSL.name("amount"), Double.class), 10.0)
                .set(DSL.field(DSL.name("received"), LocalDate.class), LocalDate.parse("2025-01-01"))
                .set(DSL.field(DSL.name("pay_before"), LocalDate.class), LocalDate.parse("2025-01-31"))
                .set(DSL.field(DSL.name("pay_timestamp"), Instant.class), Instant.parse("2025-01-22T23:59:59Z"))
                .set(DSL.field(DSL.name("is_paid"), Boolean.class), true)
                .set(DSL.field(DSL.name("content__id"), String.class), "content_1")
                .set(DSL.field(DSL.name("content__filename"), String.class), "file.pdf")
                .set(DSL.field(DSL.name("content__mimetype"), String.class), "application/pdf")
                .set(DSL.field(DSL.name("content__length"), Long.class), 100L)
                .set(DSL.field(DSL.name("audit_metadata__created_date"), Instant.class), now)
                .set(DSL.field(DSL.name("audit_metadata__created_by_name"), String.class), "bob")
                .set(DSL.field(DSL.name("audit_metadata__last_modified_date"), Instant.class), now)
                .set(DSL.field(DSL.name("audit_metadata__last_modified_by_name"), String.class), "bob")
                .set(DSL.field(DSL.name("customer"), UUID.class), ALICE_ID)
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("invoice")))
                .set(DSL.field(DSL.name("id"), UUID.class), INVOICE2_ID)
                .set(DSL.field(DSL.name("version"), Long.class), 9999L)
                .set(DSL.field(DSL.name("number"), String.class), "invoice_2")
                .set(DSL.field(DSL.name("amount"), Double.class), 20.0)
                .set(DSL.field(DSL.name("received"), LocalDate.class), LocalDate.parse("2025-02-01"))
                .set(DSL.field(DSL.name("pay_before"), LocalDate.class), LocalDate.parse("2025-02-28"))
                .set(DSL.field(DSL.name("pay_timestamp"), Instant.class), Instant.parse("2025-02-22T23:59:59Z"))
                .set(DSL.field(DSL.name("is_paid"), Boolean.class), false)
                // no content
                .set(DSL.field(DSL.name("audit_metadata__created_date"), Instant.class), now)
                .set(DSL.field(DSL.name("audit_metadata__created_by_name"), String.class), "alice")
                .set(DSL.field(DSL.name("audit_metadata__last_modified_date"), Instant.class), now)
                .set(DSL.field(DSL.name("audit_metadata__last_modified_by_name"), String.class), "alice")
                .set(DSL.field(DSL.name("customer"), UUID.class), BOB_ID)
                .set(DSL.field(DSL.name("previous_invoice"), UUID.class), INVOICE1_ID)
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("invoice")))
                .set(DSL.field(DSL.name("id"), UUID.class), INVOICE3_ID)
                .set(DSL.field(DSL.name("version"), Long.class), 9999L)
                .set(DSL.field(DSL.name("number"), String.class), "invoice_3")
                .set(DSL.field(DSL.name("amount"), Double.class), 1.0)
                .set(DSL.field(DSL.name("received"), LocalDate.class), LocalDate.parse("2025-02-01"))
                .set(DSL.field(DSL.name("pay_before"), LocalDate.class), LocalDate.parse("2025-02-28"))
                .set(DSL.field(DSL.name("pay_timestamp"), Instant.class), Instant.parse("2025-02-22T23:59:59Z"))
                .set(DSL.field(DSL.name("is_paid"), Boolean.class), false)
                .set(DSL.field(DSL.name("content__id"), String.class), "content_3")
                .set(DSL.field(DSL.name("content__filename"), String.class), "invoice.doc")
                .set(DSL.field(DSL.name("content__mimetype"), String.class), "application/msword")
                .set(DSL.field(DSL.name("content__length"), Long.class), 1048576L)
                .set(DSL.field(DSL.name("audit_metadata__created_date"), Instant.class), now)
                .set(DSL.field(DSL.name("audit_metadata__created_by_name"), String.class), "alice")
                .set(DSL.field(DSL.name("audit_metadata__last_modified_date"), Instant.class), now)
                .set(DSL.field(DSL.name("audit_metadata__last_modified_by_name"), String.class), "alice")
                .set(DSL.field(DSL.name("customer"), UUID.class), BOB_ID)
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("person__friends")))
                .set(DSL.field(DSL.name("person_src_id"), UUID.class), BOB_ID)
                .set(DSL.field(DSL.name("person_tgt_id"), UUID.class), ALICE_ID)
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("product")),
                        DSL.field(DSL.name("id"), UUID.class), DSL.field(DSL.name("code"), String.class), DSL.field(DSL.name("description"), String.class))
                .values(PRODUCT1_ID, "code_1", "test description")
                .values(PRODUCT2_ID, "code_2", "")
                .values(PRODUCT3_ID, "code_3", null)
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("invoice__products")),
                        DSL.field(DSL.name("invoice_id"), UUID.class), DSL.field(DSL.name("product_id"), UUID.class))
                .values(INVOICE1_ID, PRODUCT1_ID)
                .values(INVOICE1_ID, PRODUCT2_ID)
                .values(INVOICE2_ID, PRODUCT1_ID)
                .execute();
        dslContext.insertInto(DSL.table(DSL.name("address")), DSL.field(DSL.name("id"), UUID.class))
                .values(ADDRESS1_ID)
                .values(ADDRESS2_ID)
                .execute();
    }

    @Test
    void findAlice() {
        // entity.name = alice
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
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
        ThunkExpression<Boolean> expression = StringComparison.contentGridPrefixSearchMatch(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("name")),
                Scalar.of("ALI")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
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
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }

    @Test
    void findWithFullTextSearch() {
        ThunkExpression<Boolean> expression = StringComparison.contentGridFullTextSearchMatch(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("comment")),
                Scalar.of("bar foo"), Locale.ENGLISH

        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoSet("name", String.class);

        assertEquals(Set.of("alice", "Thĳs"), results);
    }

    @Test
    void findNormalizedWithFullTextSearch() {
        ThunkExpression<Boolean> expression = StringComparison.contentGridFullTextSearchMatch(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("comment")),
                // Actual value in table is "Thĳs", which should be normalized by search to still match this.
                Scalar.of("Thijs"), Locale.ENGLISH
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoSet("name", String.class);
        assertEquals(Set.of("Thĳs"), results);
    }

    @Test
    void findFullTextSearchInFrench() {
        ThunkExpression<Boolean> expression = StringComparison.contentGridFullTextSearchMatch(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("comment")),
                Scalar.of("baguette"), Locale.FRENCH
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, FRENCH_PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoSet("name", String.class);
        assertEquals(Set.of("jacques"), results);
    }

    @Test
    void findInvoiceCreatedByBob() {
        // entity.audit_metadata.created_by.name = bob
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name")),
                Scalar.of("bob")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }

    @Test
    void findFriendsOfAlice() {
        // entity.friends.[_].name = alice
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("friends"), SymbolicReference.pathVar("__var_x0001__"), SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(BOB_ID, result.get("id"));
    }

    @Test
    void findBothTermsSymbolicReferences_shouldUseNormalizedSearch() {
        // entity.name = entity.vat
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("name")),
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("vat"))
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(THIJS_ID, result.get("id"));

        // Without normalization, they don't match
        assertNotEquals(result.get("name"), result.get("vat"));
    }

    @Test
    void findBothTermsStringScalars_shouldUseNormalizedSearch() {
        // ĳ = ij
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                Scalar.of("ĳ"),
                Scalar.of("ij")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, PERSON);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(4, results.size());
    }

    @Test
    void findInvoiceNextOfAlice() {
        // entity.previous_invoice.customer.name = alice
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("previous_invoice"), SymbolicReference.path("customer"), SymbolicReference.path("name")),
                Scalar.of("alice")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE2_ID, result.get("id"));
    }

    @Test
    void findInvoicePrevOfBob() {
        // entity.next_invoice.customer.name = bob
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("next_invoice"), SymbolicReference.path("customer"), SymbolicReference.path("name")),
                Scalar.of("bob")
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }

    @Test
    void findInvoiceWith2relations() {
        // entity.customer.name = entity.next_invoice.audit_metadata.created_by.name
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("name")),
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("next_invoice"), SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name"))
        );
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        var result = results.getFirst();
        assertEquals(INVOICE1_ID, result.get("id"));
    }



    static Stream<Arguments> inOperatorValues() {
        return Stream.of(
                Arguments.argumentSet(
                        "singleton set",
                        INVOICE,
                        invoiceNumberInThunxExpression(new SetValue(Set.of(Scalar.of("invoice_1")))),
                        Set.of(INVOICE1_ID)
                ),
                Arguments.argumentSet(
                        "singleton list",
                        INVOICE,
                        invoiceNumberInThunxExpression(new ListValue(List.of(Scalar.of("invoice_1")))),
                        Set.of(INVOICE1_ID)
                ),
                Arguments.argumentSet(
                        "empty set",
                        INVOICE,
                        invoiceNumberInThunxExpression(new SetValue(Set.of())),
                        Set.of()
                ),
                Arguments.argumentSet(
                        "empty list",
                        INVOICE,
                        invoiceNumberInThunxExpression(new ListValue(java.util.List.of())),
                        Set.of()
                ),
                Arguments.argumentSet(
                        "set multiple elements",
                        INVOICE,
                        invoiceNumberInThunxExpression(new SetValue(Set.of(Scalar.of("invoice_1"), Scalar.of("invoice_2")))),
                        Set.of(INVOICE1_ID, INVOICE2_ID)
                ),
                Arguments.argumentSet(
                        "list multiple elements",
                        INVOICE,
                        invoiceNumberInThunxExpression(new ListValue(List.of(Scalar.of("invoice_1"), Scalar.of("invoice_2")))),
                        Set.of(INVOICE1_ID, INVOICE2_ID)
                ),
                Arguments.argumentSet(
                        "set multiple types",
                        INVOICE,
                        invoiceNumberInThunxExpression(new SetValue(Set.of(Scalar.of("invoice_1"), Scalar.of(10), Scalar.of(true)))),
                        Set.of(INVOICE1_ID)
                ),
                Arguments.argumentSet(
                        "list multiple types",
                        INVOICE,
                        invoiceNumberInThunxExpression(new ListValue(List.of(Scalar.of("invoice_1"), Scalar.of(10), Scalar.of(true)))),
                        Set.of(INVOICE1_ID)
                ),
                Arguments.argumentSet(
                        "nfkc normalized match",
                        INVOICE,
                        invoiceNumberInThunxExpression(new SetValue(Set.of(Scalar.of("invoice_¹")))),
                        Set.of(INVOICE1_ID)
                ),
                Arguments.argumentSet(
                        "double match",
                        INVOICE,
                        invoiceInThunxExpression("amount", new SetValue(Set.of(Scalar.of(10.0)))),
                        Set.of(INVOICE1_ID)
                ),
                Arguments.argumentSet(
                        "normalization match with non-normalized data",
                        PERSON,
                        Comparison.in(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("name")),
                                new SetValue(Set.of(Scalar.of("Thijs"))) // contains ij instead of ĳ
                        ),
                        Set.of(THIJS_ID)
                ),
                Arguments.argumentSet(
                        "over relation",
                        INVOICE,
                        Comparison.in(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("vat") ),
                                new SetValue(Set.of(Scalar.of("vat_1")))
                        ),
                        Set.of(INVOICE1_ID)
                )
        );
    }

    static ThunkExpression<?> invoiceNumberInThunxExpression(CollectionValue<?> value) {
        return invoiceInThunxExpression("number", value);
    }

    static ThunkExpression<?> invoiceInThunxExpression(String field, CollectionValue<?> value) {
        return Comparison.in(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path(field)),
                value
        );
    }

    @ParameterizedTest
    @MethodSource("inOperatorValues")
    void inOperator(Entity entity, ThunkExpression<Boolean> expression, Set<UUID> expectedUUids) {
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, entity);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();
        assertEquals(expectedUUids.size(), results.size());
        var uuids = results.stream()
                .map(row -> (UUID) row.get("id"))
                .toList();
        expectedUUids.forEach(uuid -> assertTrue(uuids.contains(uuid), "Expected UUID " + uuid + " to be in results"));
    }

    static Stream<ArgumentSet> allFunctions() {
        return Stream.of(
                Arguments.argumentSet("equals (double)",
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(10.0)
                        ), 1),
                Arguments.argumentSet("equals (long)",
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("content"), SymbolicReference.path("length")),
                                Scalar.of(100L)
                        ), 1),
                Arguments.argumentSet("equals (string)", // should be normalized
                        Comparison.areEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                                Scalar.of("invoice_¹") // invoice_1
                        ), 1),
                Arguments.argumentSet("not equals (double)",
                        Comparison.notEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                Scalar.of(20.0)
                        ), 2),
                Arguments.argumentSet("not equals (string)", // should be normalized
                        Comparison.notEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                                Scalar.of("invoice_²") // invoice_2
                        ), 2),
                Arguments.argumentSet("and, less than, greater than",
                        LogicalOperation.conjunction(Stream.of(
                                Comparison.greater(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                        Scalar.of(1.0)
                                ),
                                Comparison.less(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                        Scalar.of(12.0)
                                )
                        )), 1),
                Arguments.argumentSet("and, less than or equals, greater than or equals",
                        LogicalOperation.conjunction(Stream.of(
                                Comparison.greaterOrEquals(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                        Scalar.of(1.0)
                                ),
                                Comparison.lessOrEquals(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                        Scalar.of(10.0)
                                )
                        )), 2),
                Arguments.argumentSet("or with same path", // e.g. when query parameter is provided multiple times
                        LogicalOperation.disjunction(Stream.of(
                                Comparison.areEqual(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                        Scalar.of(1.0)
                                ),
                                Comparison.areEqual(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")),
                                        Scalar.of(10.0)
                                )
                        )), 2),
                Arguments.argumentSet("or with nullable relations",
                        LogicalOperation.disjunction(
                                Comparison.notEqual(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("previous_invoice"), SymbolicReference.path("number")),
                                        Scalar.of("")
                                ),
                                Comparison.notEqual(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("next_invoice"), SymbolicReference.path("number")),
                                        Scalar.of("")
                                )
                        ), 2),
                Arguments.argumentSet("not",
                        LogicalOperation.negation(
                                Comparison.areEqual(
                                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                                        Scalar.of("invoice_2")
                                )
                        ), 2),
                Arguments.argumentSet("plus",
                        Comparison.areEqual(
                                NumericFunction.plus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(10.0)),
                                Scalar.of(20.0)
                        ), 1),
                Arguments.argumentSet("multiply",
                        Comparison.areEqual(
                                NumericFunction.multiply(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(2L)),
                                Scalar.of(20.0)
                        ), 1),
                Arguments.argumentSet("minus",
                        Comparison.areEqual(
                                NumericFunction.minus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(10.0)),
                                Scalar.of(0.0)
                        ), 1),
                Arguments.argumentSet("divide",
                        Comparison.areEqual(
                                NumericFunction.divide(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(2L)),
                                Scalar.of(5.0)
                        ), 1),
                Arguments.argumentSet("modulo",
                        Comparison.areEqual(
                                NumericFunction.modulus(SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("amount")), Scalar.of(3L)),
                                Scalar.of(1.0)
                        ), 2),
                Arguments.argumentSet("normalize",
                        StringComparison.normalizedEqual(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("number")),
                                Scalar.of("invoice_¹") // invoice_1
                        ), 1),
                Arguments.argumentSet("contentgrid prefix search",
                        StringComparison.contentGridPrefixSearchMatch(
                                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("audit_metadata"), SymbolicReference.path("created_by"), SymbolicReference.path("name")),
                                Scalar.of("Bö") // bob
                        ), 1)
        );
    }

    @ParameterizedTest
    @MethodSource("allFunctions")
    void findInvoice1(ThunkExpression<Boolean> expression, int expectedSize) {
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(expectedSize, results.size());
        assertTrue(results.stream().anyMatch(result -> INVOICE1_ID.equals(result.get("id"))));
    }

    static Stream<ThunkExpression<Boolean>> illegalExpressions() {
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
    @MethodSource("illegalExpressions")
    void findIllegalExpression(ThunkExpression<Boolean> expression) {
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        assertThrows(InvalidThunkExpressionException.class, () -> VISITOR.createCondition(expression, context));
    }

    private static final Map<String, ThunkExpression<?>> ENTITY_ATTRIBUTES = Map.of(
            "STRING", SymbolicReference.parse("entity.number"),
            "NUMBER", SymbolicReference.parse("entity.amount"),
            "BOOLEAN", SymbolicReference.parse("entity.is_paid"),
            "DATE", SymbolicReference.parse("entity.received"),
            "DATETIME", SymbolicReference.parse("entity.pay_timestamp"),
            "UUID", SymbolicReference.parse("entity.id")
    );

    private static final Map<String, ThunkExpression<?>> IAM_VALUES = Map.of(
            "STRING", Scalar.of("invoice_1"),
            "NUMBER", Scalar.of(10),
            "BOOLEAN", Scalar.of(true),
            "STRING_ARRAY", new ListValue(List.of(Scalar.of("invoice_1"), Scalar.of("invoice_2"))),
            "NUMBER_ARRAY", new ListValue(List.of(Scalar.of(10), Scalar.of(20))),
            "BOOLEAN_ARRAY", new ListValue(List.of(Scalar.of(true), Scalar.of(false))),
            "EMPTY_ARRAY", new ListValue(List.of())
    );

    static Stream<Arguments> incompatibleEqualsExpressions() {
        var streamBuilder = Stream.<Arguments>builder();
        for (var attributeType : ENTITY_ATTRIBUTES.keySet()) {
            for (var valueType : IAM_VALUES.keySet()) {
                if (!attributeType.equals(valueType)) {
                    streamBuilder.add(Arguments.argumentSet("%s = %s".formatted(attributeType, valueType),
                            Comparison.areEqual(ENTITY_ATTRIBUTES.get(attributeType), IAM_VALUES.get(valueType))));
                }
            }
        }
        return streamBuilder.build();
    }

    static Stream<Arguments> incompatibleLessThanExpressions() {
        var streamBuilder = Stream.<Arguments>builder();
        for (var attributeType : ENTITY_ATTRIBUTES.keySet()) {
            for (var valueType : IAM_VALUES.keySet()) {
                if (!attributeType.equals(valueType) || Set.of("STRING", "BOOLEAN").contains(attributeType)) {
                    // type mismatch or not sortable
                    streamBuilder.add(Arguments.argumentSet("%s < %s".formatted(attributeType, valueType),
                            Comparison.less(ENTITY_ATTRIBUTES.get(attributeType), IAM_VALUES.get(valueType))));
                }
            }
        }
        return streamBuilder.build();
    }

    static Stream<Arguments> incompatibleInExpressions() {
        var streamBuilder = Stream.<Arguments>builder();
        for (var attributeType : ENTITY_ATTRIBUTES.keySet()) {
            for (var valueType : IAM_VALUES.keySet()) {
                if (!valueType.equals(attributeType + "_ARRAY")) {
                    streamBuilder.add(Arguments.argumentSet("%s in %s".formatted(attributeType, valueType),
                            Comparison.in(ENTITY_ATTRIBUTES.get(attributeType), IAM_VALUES.get(valueType))));
                }
            }
        }
        return streamBuilder.build();
    }

    static Stream<Arguments> incompatibleExpressions() {
        return Stream.concat(
                Stream.concat(
                        incompatibleEqualsExpressions(),
                        incompatibleLessThanExpressions()
                ),
                incompatibleInExpressions()
        );
    }

    @ParameterizedTest
    @MethodSource("incompatibleExpressions")
    void findExpressionWithFaultyIamConfig(ThunkExpression<Boolean> expression) {
        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var condition = VISITOR.createCondition(expression, context);
        assertEquals(DSL.falseCondition(), condition);
    }

    @Test
    void reuseVariableInSameRelation() {
        // Test that the same variable can be reused within the same relation path
        // entity.customer.friends[var].name = entity.customer.friends[var].vat
        ThunkExpression<Boolean> expression = Comparison.areEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.pathVar("__var1__"), SymbolicReference.path("name")),
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("customer"), SymbolicReference.path("friends"), SymbolicReference.pathVar("__var1__"), SymbolicReference.path("vat"))
        );

        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);

        // Insert test data
        dslContext.insertInto(DSL.table(DSL.name("person__friends")))
                .set(DSL.field(DSL.name("person_src_id"), UUID.class), ALICE_ID)
                .set(DSL.field(DSL.name("person_tgt_id"), UUID.class), THIJS_ID)
                .execute();

        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        // Should find INVOICE1 which has ALICE as customer, and ALICE has THIJS as friend
        // THIJS has name="Thĳs" and vat="Thijs" which match when normalized
        assertEquals(1, results.size());
        assertEquals(INVOICE1_ID, results.getFirst().get("id"));
    }

    @ParameterizedTest
    @CsvSource({
            "var1,var2",
            "_,_",
            "var1,_",
            "_,var2",
    })
    void differentVariablesInSameRelation(String var1, String var2) {
        // Test that different variables in the same relation create separate joins
        // entity.products[var1].code != entity.products[var2].code
        ThunkExpression<Boolean> expression = Comparison.notEqual(
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("products"), SymbolicReference.pathVar(var1), SymbolicReference.path("code")),
                SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("products"), SymbolicReference.pathVar(var2), SymbolicReference.path("code"))
        );

        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);

        // The condition checks if the invoice has two products with a different code
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        assertEquals(INVOICE1_ID, results.getFirst().get("id"));
    }

    @Test
    void underscoreVariableInDifferentRelations_allowed() {
        // Test that the underscore variable "_" can be used multiple times
        ThunkExpression<Boolean> expression = LogicalOperation.conjunction(Stream.of(
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("products"), SymbolicReference.pathVar("_"), SymbolicReference.path("code")),
                        Scalar.of("code_1")
                ),
                Comparison.areEqual(
                        SymbolicReference.of(ENTITY_VAR, SymbolicReference.path("previous_invoice"), SymbolicReference.path("products"), SymbolicReference.pathVar("_"), SymbolicReference.path("code")),
                        Scalar.of("code_2")
                )
        ));

        var context = new JOOQThunkExpressionVisitor.JOOQContext(APPLICATION, INVOICE);
        var table = JOOQUtils.resolveTable(context.getRootTable(), context.getRootAlias());
        var condition = VISITOR.createCondition(expression, context);

        // Should not throw an exception
        var results = dslContext.selectFrom(table)
                .where(condition)
                .fetch()
                .intoMaps();

        assertEquals(1, results.size());
        assertEquals(INVOICE2_ID, results.getFirst().get("id"));
    }
}

