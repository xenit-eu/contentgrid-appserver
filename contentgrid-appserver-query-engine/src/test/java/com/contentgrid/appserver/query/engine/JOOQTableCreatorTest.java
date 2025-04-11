package com.contentgrid.appserver.query.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
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
import com.contentgrid.appserver.query.engine.JOOQTableCreatorTest.TestApplication;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///"
})
@ContextConfiguration(classes = TestApplication.class)
class JOOQTableCreatorTest {

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

    private static final CompositeAttribute INVOICE_AUDIT_METADATA = CompositeAttribute.builder()
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

    private static final OneToOneRelation INVOICE_NEXT = SourceOneToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("next_invoice"))
                    .pathSegment(PathSegmentName.of("next-invoice"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("previous_invoice"))
                    .pathSegment(PathSegmentName.of("previous-invoice"))
                    .build())
            .targetReference(ColumnName.of("next_invoice"))
            .build();

    @Autowired
    private DSLContext dslContext;

    @Autowired
    private JOOQTableCreator tableCreator;

    @Test
    void applicationWithSimpleEntity() {
        var application = Application.builder()
                .name(ApplicationName.of("simple-entity-application"))
                .entity(PERSON)
                .build();

        // create tables
        tableCreator.createTables(application);

        var tablesMeta = dslContext.meta().getTables();

        // cleanup tables (so next test can run gracefully if this one fails)
        dslContext.dropTable(PERSON.getTable().getValue()).execute();

        var publicTables = tablesMeta.stream()
                .filter(tableMeta -> tableMeta.getSchema() != null && "public".equals(tableMeta.getSchema().getName()))
                .toList();

        assertEquals(1, publicTables.size());
        assertEquals("person", publicTables.getFirst().getName());

        var person = publicTables.getFirst();
        assertEquals(3, person.fields().length);
        assertNotNull(person.field("id", UUID.class));
        assertNotNull(person.field("vat", String.class));
        assertNotNull(person.field("name", String.class));
    }

    @Test
    void applicationWithAdvancedEntity() {
        var application = Application.builder()
                .name(ApplicationName.of("advanced-entity-application"))
                .entity(INVOICE)
                .build();

        // create tables
        tableCreator.createTables(application);

        var tablesMeta = dslContext.meta().getTables();

        // cleanup tables (so next test can run gracefully if this one fails)
        dslContext.dropTable(INVOICE.getTable().getValue()).execute();

        var publicTables = tablesMeta.stream()
                .filter(tableMeta -> tableMeta.getSchema() != null && "public".equals(tableMeta.getSchema().getName()))
                .toList();

        assertEquals(1, publicTables.size());
        assertEquals("invoice", publicTables.getFirst().getName());

        var invoice = publicTables.getFirst();
        assertEquals(18, invoice.fields().length);
        assertNotNull(invoice.field("id", UUID.class));
        assertNotNull(invoice.field("number", String.class));
        assertNotNull(invoice.field("amount", Double.class));
        assertNotNull(invoice.field("received", Instant.class));
        assertNotNull(invoice.field("pay_before", Instant.class));
        assertNotNull(invoice.field("is_paid", Boolean.class));
        assertNotNull(invoice.field("content__id", String.class));
        assertNotNull(invoice.field("content__filename", String.class));
        assertNotNull(invoice.field("content__mimetype", String.class));
        assertNotNull(invoice.field("content__length", Long.class));
        assertNotNull(invoice.field("audit_metadata__created_date", Instant.class));
        assertNotNull(invoice.field("audit_metadata__created_by_id", String.class));
        assertNotNull(invoice.field("audit_metadata__created_by_ns", String.class));
        assertNotNull(invoice.field("audit_metadata__created_by_name", String.class));
        assertNotNull(invoice.field("audit_metadata__last_modified_date", Instant.class));
        assertNotNull(invoice.field("audit_metadata__last_modified_by_id", String.class));
        assertNotNull(invoice.field("audit_metadata__last_modified_by_ns", String.class));
        assertNotNull(invoice.field("audit_metadata__last_modified_by_name", String.class));
    }

    static Stream<Relation> customerInvoicesRelations() {
        return Stream.of(INVOICE_CUSTOMER, INVOICE_CUSTOMER.inverse());
    }

    @ParameterizedTest
    @MethodSource("customerInvoicesRelations")
    void applicationWithRelation(Relation relation) {
        var application = Application.builder()
                .name(ApplicationName.of("relation-application"))
                .entity(INVOICE)
                .entity(PERSON)
                .relation(relation)
                .build();

        // create tables
        tableCreator.createTables(application);

        var tablesMeta = dslContext.meta().getTables();

        // cleanup tables (so next test can run gracefully if this one fails)
        dslContext.dropTable(INVOICE.getTable().getValue()).execute();
        dslContext.dropTable(PERSON.getTable().getValue()).execute();

        var publicTables = tablesMeta.stream()
                .filter(tableMeta -> tableMeta.getSchema() != null && "public".equals(tableMeta.getSchema().getName()))
                .toList();

        assertEquals(2, publicTables.size());
        var person = publicTables.stream()
                .filter(table -> table.getName().equals("person")).findAny()
                .orElseThrow();
        var invoice = publicTables.stream()
                .filter(table -> table.getName().equals("invoice")).findAny()
                .orElseThrow();

        assertEquals(3, person.fields().length); // unchanged
        assertEquals(19, invoice.fields().length);
        assertNotNull(invoice.field("customer", UUID.class));
    }

    @Test
    void applicationWithManyToMany() {
        var application = Application.builder()
                .name(ApplicationName.of("many-to-many-application"))
                .entity(PERSON)
                .relation(PERSON_FRIENDS)
                .build();

        // create tables
        tableCreator.createTables(application);

        var tablesMeta = dslContext.meta().getTables();

        // cleanup tables (so next test can run gracefully if this one fails)
        dslContext.dropTable(PERSON_FRIENDS.getJoinTable().getValue()).execute();
        dslContext.dropTable(PERSON.getTable().getValue()).execute();

        var publicTables = tablesMeta.stream()
                .filter(tableMeta -> tableMeta.getSchema() != null && "public".equals(tableMeta.getSchema().getName()))
                .toList();

        assertEquals(2, publicTables.size());
        var person = publicTables.stream()
                .filter(table -> table.getName().equals("person")).findAny()
                .orElseThrow();
        var joinTable = publicTables.stream()
                .filter(table -> table.getName().equals("person__friends")).findAny()
                .orElseThrow();

        assertEquals(3, person.fields().length); // unchanged
        assertEquals(2, joinTable.fields().length);
        assertNotNull(joinTable.field("person_src_id", UUID.class));
        assertNotNull(joinTable.field("person_tgt_id", UUID.class));
    }

    static Stream<Relation> oneToOneRelations() {
        return Stream.of(INVOICE_NEXT, INVOICE_NEXT.inverse());
    }

    @ParameterizedTest
    @MethodSource("oneToOneRelations")
    void applicationWithOneToOne(Relation oneToOneRelation) {
        var application = Application.builder()
                .name(ApplicationName.of("one-to-one-application"))
                .entity(INVOICE)
                .relation(oneToOneRelation)
                .build();

        // create tables
        tableCreator.createTables(application);

        var tablesMeta = dslContext.meta().getTables();

        // cleanup tables (so next test can run gracefully if this one fails)
        dslContext.dropTable(INVOICE.getTable().getValue()).execute();

        var publicTables = tablesMeta.stream()
                .filter(tableMeta -> tableMeta.getSchema() != null && "public".equals(tableMeta.getSchema().getName()))
                .toList();

        assertEquals(1, publicTables.size());
        assertEquals("invoice", publicTables.getFirst().getName());

        var invoice = publicTables.getFirst();

        assertEquals(19, invoice.fields().length);
        assertNotNull(invoice.field("next_invoice", UUID.class));
        assertNull(invoice.field("previous_invoice", UUID.class));
    }

    @Test
    void invalidApplication_rollbackTransaction() {
        // Table names are too long, so they are capped at the postgres limit.
        // And then they have the same name.
        var application = Application.builder()
                .name(ApplicationName.of("invalid-application"))
                .entity(Entity.builder()
                        .name(EntityName.of("foo"))
                        .pathSegment(PathSegmentName.of("foo"))
                        .table(TableName.of("a_very_long_database_table_name_that_should_be_longer_than_the_postgres_limit"))
                        .build())
                .entity(Entity.builder()
                        .name(EntityName.of("bar"))
                        .pathSegment(PathSegmentName.of("bar"))
                        .table(TableName.of("a_very_long_database_table_name_that_should_be_longer_than_the_postgres_limit_too"))
                        .build())
                .build();

        assertThrows(BadSqlGrammarException.class, () -> tableCreator.createTables(application));

        // Check no public tables exist
        var tablesMeta = dslContext.meta().getTables();

        var publicTables = tablesMeta.stream()
                .filter(tableMeta -> tableMeta.getSchema() != null && "public".equals(tableMeta.getSchema().getName()))
                .toList();

        assertTrue(publicTables.isEmpty());
    }

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }

        @Bean
        public JOOQTableCreator jooqTableCreator(DSLContext dslContext) {
            return new JOOQTableCreator(dslContext);
        }
    }
}