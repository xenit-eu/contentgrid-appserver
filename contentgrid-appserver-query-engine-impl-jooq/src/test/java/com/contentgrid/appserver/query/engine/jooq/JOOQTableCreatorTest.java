package com.contentgrid.appserver.query.engine.jooq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.contentgrid.appserver.query.engine.jooq.JOOQTableCreatorTest.TestApplication;
import com.contentgrid.appserver.query.engine.api.TableCreator;
import com.contentgrid.appserver.query.engine.jooq.resolver.AutowiredDSLContextResolver;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///"
})
@ContextConfiguration(classes = TestApplication.class)
@Slf4j
@Transactional
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
    private TableCreator tableCreator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Map<String, String> getColumnInfo(String dbSchema, String tableName) {
        // Use JdbcTemplate's execute method with a ConnectionCallback
        // This gives access to the Connection while managing resources correctly.
        return jdbcTemplate.execute((Connection con) -> {
            Map<String, String> columnData = new HashMap<>();
            DatabaseMetaData metaData = con.getMetaData();

            log.debug("Querying metadata for columns in table: %s.%s%n", dbSchema, tableName);

            try (ResultSet columns = metaData.getColumns(null, dbSchema, tableName, null)) {
                boolean foundColumn = false;
                while (columns.next()) {
                    foundColumn = true;
                    String columnName = columns.getString("COLUMN_NAME");
                    String typeName = columns.getString("TYPE_NAME");
                    int dataType = columns.getInt("DATA_TYPE");

                   log.debug("Found column: %s, DB Type: %s, SQL Type: %d%n",
                            columnName, typeName, dataType);

                    // Store details (lowercase key for consistent comparison)
                    columnData.put(columnName, typeName);
                }
                if (!foundColumn) {
                    // Fail inside the callback if necessary, or return empty map and check outside
                    Assertions.fail("No columns found for table '" + tableName + "' in schema '" + dbSchema + "'. Check table/schema names and permissions.");
                }
            }
            return columnData; // Return the map from the callback
        });
    }

    private Map<String, String> getForeignKeys(String dbSchema, String tableName) {
        // Get all foreign key columns from the given table
        return jdbcTemplate.execute((Connection con) -> {
            Map<String, String> foreignKeyData = new HashMap<>();
            DatabaseMetaData metaData = con.getMetaData();

            log.debug("Querying metadata for foreign keys in table: %s.%s%n", dbSchema, tableName);

            try (ResultSet columns = metaData.getImportedKeys(null, dbSchema, tableName)) {
                while (columns.next()) {
                    String foreignKey = columns.getString("FKCOLUMN_NAME");
                    String table = columns.getString("PKTABLE_NAME");
                    foreignKeyData.put(foreignKey, table);
                }
            }
            return foreignKeyData; // Return the map from the callback
        });
    }

    @Test
    void applicationWithSimpleEntity() {
        var application = Application.builder()
                .name(ApplicationName.of("simple-entity-application"))
                .entity(PERSON)
                .build();

        // create tables
        tableCreator.createTables(application);

        var columnInfo = getColumnInfo("public", "person");

        assertEquals(3, columnInfo.size());
        assertEquals("uuid", columnInfo.get("id"));
        assertEquals("text", columnInfo.get("vat"));
        assertEquals("text", columnInfo.get("name"));
    }

    @Test
    void applicationWithAdvancedEntity() {
        var application = Application.builder()
                .name(ApplicationName.of("advanced-entity-application"))
                .entity(INVOICE)
                .build();

        // create tables
        tableCreator.createTables(application);

        var columnInfo = getColumnInfo("public", "invoice");

        assertEquals(18, columnInfo.size());
        assertEquals("uuid", columnInfo.get("id"));
        assertEquals("text", columnInfo.get("number"));
        assertDecimal(columnInfo.get("amount"));
        assertEquals("timestamptz", columnInfo.get("received"));
        assertEquals("timestamptz", columnInfo.get("pay_before"));
        assertBoolean(columnInfo.get("is_paid"));
        assertEquals("text", columnInfo.get("content__id"));
        assertEquals("text", columnInfo.get("content__filename"));
        assertEquals("text", columnInfo.get("content__mimetype"));
        assertBigInt(columnInfo.get("content__length"));
        assertEquals("timestamptz", columnInfo.get("audit_metadata__created_date"));
        assertEquals("text", columnInfo.get("audit_metadata__created_by_id"));
        assertEquals("text", columnInfo.get("audit_metadata__created_by_ns"));
        assertEquals("text", columnInfo.get("audit_metadata__created_by_name"));
        assertEquals("timestamptz", columnInfo.get("audit_metadata__last_modified_date"));
        assertEquals("text", columnInfo.get("audit_metadata__last_modified_by_id"));
        assertEquals("text", columnInfo.get("audit_metadata__last_modified_by_ns"));
        assertEquals("text", columnInfo.get("audit_metadata__last_modified_by_name"));
    }

    // Decimal and numeric are synonyms in PostgreSQL
    static void assertDecimal(String columnType) {
        if (! (columnType.equals("decimal") || columnType.equals("numeric"))) {
            Assertions.fail("Type is not decimal or numeric: " + columnType);
        }
    }

    static void assertBoolean(String columnType) {
        if (! (columnType.equals("bool") || columnType.equals("boolean"))) {
            Assertions.fail("Type is not boolean: " + columnType);
        }
    }

    static void assertBigInt(String columnType) {
        if (! (columnType.equals("bigint") || columnType.equals("int8"))) {
            Assertions.fail("Type is not bigint: " + columnType);
        }
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

        var personInfo = getColumnInfo("public", "person");
        var invoiceInfo = getColumnInfo("public", "invoice");
        var invoiceForeignKeys = getForeignKeys("public", "invoice");

        assertEquals(3, personInfo.size()); // unchanged
        assertEquals(19, invoiceInfo.size());
        assertEquals("uuid", invoiceInfo.get("customer"));

        assertEquals(1, invoiceForeignKeys.size());
        assertEquals("person", invoiceForeignKeys.get("customer"));
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

        var personInfo = getColumnInfo("public", "person");
        var joinTableInfo = getColumnInfo("public", "person__friends");
        var joinTableForeignKeys = getForeignKeys("public", "person__friends");

        assertEquals(3, personInfo.size()); // unchanged
        assertEquals(2, joinTableInfo.size());
        assertEquals("uuid", joinTableInfo.get("person_src_id"));
        assertEquals("uuid", joinTableInfo.get("person_tgt_id"));

        assertEquals(2, joinTableForeignKeys.size());
        assertEquals("person", joinTableForeignKeys.get("person_src_id"));
        assertEquals("person", joinTableForeignKeys.get("person_tgt_id"));
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

        var columnInfo = getColumnInfo("public", "invoice");
        var foreignKeys = getForeignKeys("public", "invoice");

        assertEquals(19, columnInfo.size());
        assertEquals("uuid", columnInfo.get("next_invoice"));
        assertNull(columnInfo.get("previous_invoice"));

        assertEquals(1, foreignKeys.size());
        assertEquals("invoice", foreignKeys.get("next_invoice"));
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
    }

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }

        @Bean
        public TableCreator jooqTableCreator(DSLContext dslContext) {
            return new JOOQTableCreator(new AutowiredDSLContextResolver(dslContext));
        }
    }
}