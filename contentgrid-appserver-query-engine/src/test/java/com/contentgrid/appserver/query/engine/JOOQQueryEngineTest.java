package com.contentgrid.appserver.query.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
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
import com.contentgrid.appserver.query.engine.JOOQQueryEngineTest.TestApplication;
import com.contentgrid.appserver.query.engine.model.CGInsert;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:15:///"
})
@ContextConfiguration(classes = {TestApplication.class})
class JOOQQueryEngineTest {

    private static final Attribute INVOICE_NUMBER = SimpleAttribute.builder()
            .name(AttributeName.of("number"))
            .column(ColumnName.of("number"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .constraint(Constraint.unique())
            .build();

    private static final Attribute INVOICE_DOCUMENT = ContentAttribute.builder()
            .name(AttributeName.of("document"))
            .pathSegment(PathSegmentName.of("document"))
            .idColumn(ColumnName.of("document__id"))
            .filenameColumn(ColumnName.of("document__filename"))
            .mimetypeColumn(ColumnName.of("document__mimetype"))
            .lengthColumn(ColumnName.of("document__length"))
            .build();

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoice"))
            .table(TableName.of("invoice"))
            .attribute(INVOICE_NUMBER)
            .attribute(INVOICE_DOCUMENT)
            .searchFilter(ExactSearchFilter.builder()
                    .attribute((SimpleAttribute) INVOICE_NUMBER)
                    .name(FilterName.of("number"))
                    .build())
            .build();

    private static final Attribute CUSTOMER_NAME = SimpleAttribute.builder()
            .name(AttributeName.of("name"))
            .column(ColumnName.of("name"))
            .type(Type.TEXT)
            .constraint(Constraint.required())
            .build();

    private static final Attribute CUSTOMER_VAT = SimpleAttribute.builder()
            .name(AttributeName.of("vat"))
            .column(ColumnName.of("vat"))
            .type(Type.TEXT)
            .constraint(Constraint.unique())
            .constraint(Constraint.required())
            .build();

    private static final Entity CUSTOMER = Entity.builder()
            .name(EntityName.of("customer"))
            .table(TableName.of("customer"))
            .pathSegment(PathSegmentName.of("customer"))
            .attribute(CUSTOMER_NAME)
            .attribute(CUSTOMER_VAT)
            .searchFilter(ExactSearchFilter.builder()
                    .attribute((SimpleAttribute) CUSTOMER_VAT)
                    .name(FilterName.of("vat"))
                    .build())
            .searchFilter(PrefixSearchFilter.builder()
                    .attribute((SimpleAttribute) CUSTOMER_NAME)
                    .name(FilterName.of("name~prefix"))
                    .build())
            .build();

    private static final ManyToOneRelation INVOICE_CUSTOMER = ManyToOneRelation.builder()
            .sourceEndPoint(RelationEndPoint.builder()
                    .entity(INVOICE)
                    .name(RelationName.of("customer"))
                    .pathSegment(PathSegmentName.of("customer"))
                    .build())
            .targetEndPoint(RelationEndPoint.builder()
                    .entity(CUSTOMER)
                    .name(RelationName.of("invoices"))
                    .pathSegment(PathSegmentName.of("invoices"))
                    .build())
            .targetReference(ColumnName.of("customer"))
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("InvoicingTestApplication"))
            .entity(INVOICE)
            .entity(CUSTOMER)
            .relation(INVOICE_CUSTOMER)
            .build();

    @Autowired
    DSLContext dslContext;

    QueryEngine queryEngine;

    @BeforeEach
    public void setup() {
        this.queryEngine = new JOOQQueryEngine(dslContext);
        dslContext.createTableIfNotExists(CUSTOMER.getTable().getValue())
                .column(DSL.field(CUSTOMER.getPrimaryKey().getColumn().getValue(), UUID.class))
                .column(DSL.field(((SimpleAttribute) CUSTOMER_NAME).getColumn().getValue(), String.class))
                .column(DSL.field(((SimpleAttribute) CUSTOMER_VAT).getColumn().getValue(), String.class))
                .primaryKey(CUSTOMER.getPrimaryKey().getColumn().getValue())
                .constraint(DSL.unique(((SimpleAttribute) CUSTOMER_VAT).getColumn().getValue()))
                .execute();
        dslContext.createTableIfNotExists(INVOICE.getTable().getValue())
                .column(DSL.field(INVOICE.getPrimaryKey().getColumn().getValue(), UUID.class))
                .column(DSL.field(((SimpleAttribute) INVOICE_NUMBER).getColumn().getValue(), String.class))
                .column(DSL.field(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getId()).getColumn().getValue(), String.class))
                .column(DSL.field(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getFilename()).getColumn().getValue(), String.class))
                .column(DSL.field(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getMimetype()).getColumn().getValue(), String.class))
                .column(DSL.field(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getLength()).getColumn().getValue(), Long.class))
                .column(DSL.field(INVOICE_CUSTOMER.getTargetReference().getValue(), UUID.class))
                .primaryKey(INVOICE.getPrimaryKey().getColumn().getValue())
                .constraint(DSL.foreignKey(INVOICE_CUSTOMER.getTargetReference().getValue())
                        .references(CUSTOMER.getTable().getValue(), CUSTOMER.getPrimaryKey().getColumn().getValue()))
                .constraint(DSL.unique(((SimpleAttribute) INVOICE_NUMBER).getColumn().getValue()))
                .execute();
    }

    @AfterEach
    public void cleanup() {
        this.queryEngine.deleteAll(INVOICE.getTable());
        this.queryEngine.deleteAll(CUSTOMER.getTable());
    }

    @Test
    void createEntity() {
        var insert = CGInsert.builder()
                .table(INVOICE.getTable())
                .primaryKey(INVOICE.getPrimaryKey().getColumn())
                .pair(((SimpleAttribute) INVOICE_NUMBER).getColumn(), "invoice-123")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getId()).getColumn(), "document-id-123")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getFilename()).getColumn(), "invoice-123.pdf")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getMimetype()).getColumn(), "application/pdf")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getLength()).getColumn(), 128L)
//                .pair(INVOICE_CUSTOMER.getTargetReference(), null)
                .build();
        var invoiceId = queryEngine.create(insert);
        var invoiceData = queryEngine.findById(INVOICE.getTable(), INVOICE.getPrimaryKey().getColumn(), invoiceId).orElseThrow();
        assertEquals(invoiceId, invoiceData.get(INVOICE.getPrimaryKey().getColumn()));
        assertEquals("invoice-123", invoiceData.get(((SimpleAttribute) INVOICE_NUMBER).getColumn()));
        assertEquals("document-id-123", invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getId()).getColumn()));
        assertEquals("invoice-123.pdf", invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getFilename()).getColumn()));
        assertEquals("application/pdf", invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getMimetype()).getColumn()));
        assertEquals(128L, invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getLength()).getColumn()));
        assertNull(invoiceData.get(INVOICE_CUSTOMER.getTargetReference()));
    }

    @Test
    void createEntityWithRelation() {
        var insertCustomer = CGInsert.builder()
                .table(CUSTOMER.getTable())
                .primaryKey(CUSTOMER.getPrimaryKey().getColumn())
                .pair(((SimpleAttribute) CUSTOMER_VAT).getColumn(), "VAT-0000")
                .pair(((SimpleAttribute) CUSTOMER_NAME).getColumn(), "test")
                .build();
        var customerId = queryEngine.create(insertCustomer);
        var insertInvoice = CGInsert.builder()
                .table(INVOICE.getTable())
                .primaryKey(INVOICE.getPrimaryKey().getColumn())
                .pair(((SimpleAttribute) INVOICE_NUMBER).getColumn(), "invoice-123")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getId()).getColumn(), "document-id-123")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getFilename()).getColumn(), "invoice-123.pdf")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getMimetype()).getColumn(), "application/pdf")
                .pair(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getLength()).getColumn(), 128L)
                .pair(INVOICE_CUSTOMER.getTargetReference(), customerId)
                .build();
        var invoiceId = queryEngine.create(insertInvoice);
        var invoiceData = queryEngine.findById(INVOICE.getTable(), INVOICE.getPrimaryKey().getColumn(), invoiceId).orElseThrow();
        assertEquals(invoiceId, invoiceData.get(INVOICE.getPrimaryKey().getColumn()));
        assertEquals("invoice-123", invoiceData.get(((SimpleAttribute) INVOICE_NUMBER).getColumn()));
        assertEquals("document-id-123", invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getId()).getColumn()));
        assertEquals("invoice-123.pdf", invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getFilename()).getColumn()));
        assertEquals("application/pdf", invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getMimetype()).getColumn()));
        assertEquals(128L, invoiceData.get(((SimpleAttribute) ((ContentAttribute) INVOICE_DOCUMENT).getLength()).getColumn()));
        assertEquals(customerId, invoiceData.get(INVOICE_CUSTOMER.getTargetReference()));
    }

    @SpringBootApplication
    static class TestApplication {
        public static void main(String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }
}