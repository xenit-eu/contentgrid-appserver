package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("Invoice"))
            .table(TableName.of("invoice"))
            .attribute(SimpleAttribute.builder().name(AttributeName.of("invoiceNumber"))
                    .column(ColumnName.of("invoice_number")).type(Type.TEXT).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("amount")).column(ColumnName.of("amount"))
                    .type(Type.DOUBLE).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("isPaid")).column(ColumnName.of("is_paid"))
                    .type(Type.BOOLEAN).build())
            .attribute(ContentAttribute.builder().name(AttributeName.of("content")).build())
            .build();

    private static final Entity CUSTOMER = Entity.builder()
            .name(EntityName.of("Customer"))
            .table(TableName.of("customer"))
            .attribute(SimpleAttribute.builder().name(AttributeName.of("name")).column(ColumnName.of("name"))
                    .description("The name of the customer").type(Type.TEXT).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("email")).column(ColumnName.of("email"))
                    .description("The email of the customer").type(Type.TEXT).build())
            .build();

    private static final Relation MANY_TO_ONE = ManyToOneRelation.builder()
            .source(Relation.RelationEndPoint.builder().name(RelationName.of("customer")).entity(INVOICE)
                    .description("The customer of the invoice").build())
            .target(Relation.RelationEndPoint.builder().name(RelationName.of("invoices")).entity(CUSTOMER)
                    .description("The invoices of the customer").build())
            .targetReference(ColumnName.of("customer_id"))
            .build();

    @Test
    void invoiceApplicationTest() {
        var application = Application.builder()
                .name(ApplicationName.of("invoiceApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .build();

        assertEquals(CUSTOMER,
                application.getRelationForEntity(INVOICE, RelationName.of("customer")).orElseThrow().getTarget()
                        .getEntity());
        assertEquals(INVOICE, application.getEntityByName(EntityName.of("Invoice")).orElseThrow());
        assertEquals(INVOICE,
                application.getRelationForEntity(EntityName.of("Customer"), RelationName.of("invoices")).orElseThrow()
                        .getSource().getEntity());
    }

    @Test
    void application_duplicateEntityName() {
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateEntityApplication"))
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name(INVOICE.getName())
                        .table(TableName.of("other_table"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateTableName() {
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateTableApplication"))
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name(EntityName.of("other_entity"))
                        .table(INVOICE.getTable())
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateRelationSource() {
        // relation1.source = relation2.source
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateRelationApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .relation(ManyToManyRelation.builder()
                        .source(MANY_TO_ONE.getSource())
                        .target(RelationEndPoint.builder().name(RelationName.of("name_on_target")).entity(CUSTOMER)
                                .build())
                        .joinTable(TableName.of("join_table"))
                        .sourceReference(ColumnName.of("ref_on_source"))
                        .targetReference(ColumnName.of("ref_on_target"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateRelationTarget() {
        // relation1.target = relation2.target
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateRelationApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .relation(OneToOneRelation.builder()
                        .source(RelationEndPoint.builder().name(RelationName.of("name_on_source")).entity(INVOICE)
                                .build())
                        .target(MANY_TO_ONE.getTarget())
                        .targetReference(ColumnName.of("ref_on_target"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateRelationSourceWithTarget() {
        // relation1.source = relation2.target
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateRelationApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .relation(OneToManyRelation.builder()
                        .source(RelationEndPoint.builder().name(RelationName.of("name_on_source")).entity(CUSTOMER)
                                .build())
                        .target(MANY_TO_ONE.getSource())
                        .sourceReference(ColumnName.of("ref_on_source"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_nonExistingSourceEntity() {
        var builder = Application.builder()
                .name(ApplicationName.of("nonExistingEntityApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(ManyToOneRelation.builder()
                        .source(RelationEndPoint.builder()
                                .entity(Entity.builder().name(EntityName.of("Non-existing"))
                                        .table(TableName.of("non_existing")).build())
                                .name(RelationName.of("name_on_source"))
                                .build())
                        .target(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name(RelationName.of("name_on_target"))
                                .build())
                        .targetReference(ColumnName.of("ref_on_target"))
                        .build());
        assertThrows(EntityNotFoundException.class, builder::build);
    }

    @Test
    void application_nonExistingTargetEntity() {
        var builder = Application.builder()
                .name(ApplicationName.of("nonExistingEntityApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(ManyToOneRelation.builder()
                        .source(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name(RelationName.of("name_on_source"))
                                .build())
                        .target(RelationEndPoint.builder()
                                .entity(Entity.builder().name(EntityName.of("Non-existing"))
                                        .table(TableName.of("non_existing")).build())
                                .name(RelationName.of("name_on_target"))
                                .build())
                        .targetReference(ColumnName.of("ref_on_target"))
                        .build());
        assertThrows(EntityNotFoundException.class, builder::build);
    }

    /**
     * Test if we can create the application we use for integration testing:
     * https://console.contentgrid.com/app-frontend/integration-test-do-not-touch
     */
    @Test
    void application_integration_testing() {

        var customerId = SimpleAttribute.builder().type(Type.UUID).name(AttributeName.of("id")).build();
        var customerName = SimpleAttribute.builder().type(Type.TEXT)
                .name(AttributeName.of("name"))
                .description("the name of the customer")
                .build();
        var customerEmail = SimpleAttribute.builder().type(Type.TEXT)
                .name(AttributeName.of("email"))
                .description("the email of the customer")
                .constraint(Constraint.unique())
                .build();

        var customerPhone = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("phone_number"))
                .description("phone number of the customer")
                .build();

        var customer = Entity.builder()
                .name(EntityName.of("customer"))
                .primaryKey(customerId)
                .attribute(customerName)
                .searchFilter(
                        PrefixSearchFilter.builder().attribute(customerName).name(FilterName.of("name~prefix")).build())
                .attribute(customerEmail)
                .attribute(customerPhone)
                .build();

        var orderId = SimpleAttribute.builder().type(Type.UUID).name(AttributeName.of("id")).build();

        var orderNumber = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("order_number"))
                .description("the order number")
                .constraint(Constraint.unique())
                .build();

        var orderTotalAmount = SimpleAttribute.builder().type(Type.DOUBLE).name(AttributeName.of("total_amount"))
                .description("the total amount of the order")
                .build();

        var orderDocument = ContentAttribute.builder().name(AttributeName.of("document"))
                .description("A file attachment representing the document associated with the order.")
                .build();

        var orderAudit = CompositeAttribute.builder()
                .name(AttributeName.of("auditing"))
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("created_by"))
                        .columnPrefix(ColumnName.of("auditing__created_by_"))
                        .flag(CreatorFlag.builder().build())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("created_date"))
                        .column(ColumnName.of("auditing__created_date"))
                        .type(Type.DATETIME)
                        .flag(CreatedDateFlag.builder().build())
                        .build())
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("last_modified_by"))
                        .columnPrefix(ColumnName.of("auditing__last_modified_by_"))
                        .flag(ModifierFlag.builder().build())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("last_modified_date"))
                        .column(ColumnName.of("auditing__last_modified_date"))
                        .type(Type.DATETIME)
                        .flag(ModifiedDateFlag.builder().build())
                        .build())
                .build();

        var order = Entity.builder()
                .name(EntityName.of("order"))
                .description("Represents an order placed by a customer, consisting of various products.")
                .primaryKey(orderId)
                .attribute(orderNumber)
                .searchFilter(
                        ExactSearchFilter.builder().attribute(orderNumber).build()
                )
                .attribute(orderTotalAmount)
                .attribute(orderDocument)
                .attribute(orderAudit)
                .build();

        var productName = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("name"))
                .description("the name of the product")
                .build();

        var productCode = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("code"))
                .description("the code of the product")
                .constraint(Constraint.unique())
                .build();

        var productCategory = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("category"))
                .description("the category of the product")
                .constraint(Constraint.allowedValues(List.of("electronics", "clothing", "books")))
                .build();

        var product = Entity.builder()
                .name(EntityName.of("product"))
                .primaryKey(SimpleAttribute.builder().type(Type.UUID).name(AttributeName.of("id")).build())
                .attribute(productName)
                .searchFilter(PrefixSearchFilter.builder().attribute(productName).build())
                .attribute(SimpleAttribute.builder().type(Type.DOUBLE).name(AttributeName.of("price")).build())
                .attribute(productCode)
                .attribute(productCategory)
                .searchFilter(ExactSearchFilter.builder().attribute(productCategory).build())
                .build();

        var application = Application.builder()
                .name(ApplicationName.of("integration-test-do-not-touch"))
                .entity(customer)
                .entity(order)
                .relation(
                        OneToManyRelation.builder()
                                .source(RelationEndPoint.builder().entity(customer).name(RelationName.of("orders"))
                                        .description("Represents the orders placed by a customer.")
                                        .build())
                                .target(RelationEndPoint.builder().entity(order).build())
                                .sourceReference(ColumnName.of("_customer_id__orders"))
                                .build()
                )
                .entity(product)
                .relation(
                        ManyToManyRelation.builder()
                                .source(RelationEndPoint.builder().entity(order).name(RelationName.of("products"))
                                        .description("Represents the products in an order.")
                                        .build())
                                .target(RelationEndPoint.builder().entity(product).build())
                                .joinTable(TableName.of("order_product"))
                                .sourceReference(ColumnName.of("_order_id"))
                                .targetReference(ColumnName.of("_product_id"))
                                .build()
                )
                .build();

        assertEquals(customer, application.getEntityByName(EntityName.of("customer")).orElseThrow());
    }

}