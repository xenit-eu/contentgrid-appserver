package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.exceptions.AttributeNotFoundException;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityDefinitionNotFoundException;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.exceptions.RelationNotFoundException;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
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
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApplicationTest {

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("Invoice"))
            .table(TableName.of("invoice"))
            .pathSegment(PathSegmentName.of("invoices"))
            .linkName(LinkName.of("invoices"))
            .attribute(SimpleAttribute.builder().name(AttributeName.of("invoiceNumber"))
                    .column(ColumnName.of("invoice_number")).type(Type.TEXT).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("amount")).column(ColumnName.of("amount"))
                    .type(Type.DOUBLE).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("isPaid")).column(ColumnName.of("is_paid"))
                    .type(Type.BOOLEAN).build())
            .attribute(ContentAttribute.builder().name(AttributeName.of("content"))
                    .pathSegment(PathSegmentName.of("content"))
                    .linkName(LinkName.of("content"))
                    .idColumn(ColumnName.of("content__id"))
                    .filenameColumn(ColumnName.of("content__filename"))
                    .mimetypeColumn(ColumnName.of("content__mimetype"))
                    .lengthColumn(ColumnName.of("content__length"))
                    .build())
            .searchFilter(ExactSearchFilter.builder()
                    .attributePath(PropertyPath.of(AttributeName.of("invoiceNumber")))
                    .name(FilterName.of("invoiceNumber"))
                    .build())
            .build();

    private static final Entity CUSTOMER = Entity.builder()
            .name(EntityName.of("Customer"))
            .table(TableName.of("customer"))
            .pathSegment(PathSegmentName.of("customers"))
            .linkName(LinkName.of("customers"))
            .attribute(SimpleAttribute.builder().name(AttributeName.of("name")).column(ColumnName.of("name"))
                    .description("The name of the customer").type(Type.TEXT).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("email")).column(ColumnName.of("email"))
                    .description("The email of the customer").type(Type.TEXT).build())
            .searchFilter(PrefixSearchFilter.builder()
                    .attributePath(PropertyPath.of(AttributeName.of("name")))
                    .name(FilterName.of("name~prefix"))
                    .build())
            .build();

    private static final Relation MANY_TO_ONE = ManyToOneRelation.builder()
            .sourceEndPoint(Relation.RelationEndPoint.builder().name(RelationName.of("customer")).entity(INVOICE)
                    .pathSegment(PathSegmentName.of("customer")).linkName(LinkName.of("customer"))
                    .description("The customer of the invoice").build())
            .targetEndPoint(Relation.RelationEndPoint.builder().name(RelationName.of("invoices")).entity(CUSTOMER)
                    .pathSegment(PathSegmentName.of("invoices")).linkName(LinkName.of("invoices"))
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
                application.getRequiredRelationForEntity(INVOICE, RelationName.of("customer")).getTargetEndPoint()
                        .getEntity());
        assertEquals(INVOICE, application.getRequiredEntityByName(EntityName.of("Invoice")));
        assertEquals(CUSTOMER,
                application.getRequiredRelationForEntity(EntityName.of("Customer"), RelationName.of("invoices"))
                        .getSourceEndPoint().getEntity());
        assertEquals(INVOICE, application.getEntityByPathSegment(PathSegmentName.of("invoices")).orElseThrow());
        assertEquals(CUSTOMER, application.getRelationForPath(PathSegmentName.of("invoices"), PathSegmentName.of("customer"))
                .orElseThrow().getTargetEndPoint().getEntity());
    }

    @Test
    void application_duplicateEntityName() {
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateEntityApplication"))
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name(INVOICE.getName())
                        .table(TableName.of("other_table"))
                        .pathSegment(PathSegmentName.of("other-segment"))
                        .linkName(LinkName.of("other-link"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateTableName() {
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateTableApplication"))
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name(EntityName.of("other-entity"))
                        .table(INVOICE.getTable())
                        .pathSegment(PathSegmentName.of("other-segment"))
                        .linkName(LinkName.of("other-link"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateTableName_withJoinTable() {
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateTableApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(ManyToManyRelation.builder()
                        .sourceEndPoint(MANY_TO_ONE.getSourceEndPoint())
                        .targetEndPoint(MANY_TO_ONE.getTargetEndPoint())
                        .joinTable(INVOICE.getTable())
                        .sourceReference(ColumnName.of("source_id"))
                        .targetReference(ColumnName.of("target_id"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicatePathSegment() {
        var builder = Application.builder()
                .name(ApplicationName.of("duplicatePathSegmentApplication"))
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name(EntityName.of("other-entity"))
                        .table(TableName.of("other_table"))
                        .pathSegment(INVOICE.getPathSegment())
                        .linkName(LinkName.of("other-link"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateLinkName() {
        var builder = Application.builder()
                .name(ApplicationName.of("duplicateLinkNameApplication"))
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name(EntityName.of("other-entity"))
                        .table(TableName.of("other_table"))
                        .pathSegment(PathSegmentName.of("other-segment"))
                        .linkName(INVOICE.getLinkName())
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
                        .sourceEndPoint(MANY_TO_ONE.getSourceEndPoint())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(CUSTOMER)
                                .name(RelationName.of("name_on_target"))
                                .pathSegment(PathSegmentName.of("segment-on-target"))
                                .linkName(LinkName.of("rel_on_target"))
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
                .relation(SourceOneToOneRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name(RelationName.of("name_on_source"))
                                .pathSegment(PathSegmentName.of("segment-on-source"))
                                .linkName(LinkName.of("rel_on_source"))
                                .build())
                        .targetEndPoint(MANY_TO_ONE.getTargetEndPoint())
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
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(CUSTOMER)
                                .name(RelationName.of("name_on_source"))
                                .pathSegment(PathSegmentName.of("segment-on-source"))
                                .linkName(LinkName.of("rel_on_source"))
                                .build())
                        .targetEndPoint(MANY_TO_ONE.getSourceEndPoint())
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
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(Entity.builder()
                                        .name(EntityName.of("Non-existing"))
                                        .table(TableName.of("non_existing"))
                                        .pathSegment(PathSegmentName.of("non-existings"))
                                        .linkName(LinkName.of("non-existings"))
                                        .build())
                                .name(RelationName.of("name_on_source"))
                                .pathSegment(PathSegmentName.of("segment-on-source"))
                                .linkName(LinkName.of("rel_on_source"))
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name(RelationName.of("name_on_target"))
                                .pathSegment(PathSegmentName.of("segment-on-target"))
                                .linkName(LinkName.of("rel_on_target"))
                                .build())
                        .targetReference(ColumnName.of("ref_on_source"))
                        .build());
        assertThrows(EntityDefinitionNotFoundException.class, builder::build);
    }

    @Test
    void application_nonExistingTargetEntity() {
        var builder = Application.builder()
                .name(ApplicationName.of("nonExistingEntityApplication"))
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(ManyToOneRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name(RelationName.of("name_on_source"))
                                .pathSegment(PathSegmentName.of("segment-on-source"))
                                .linkName(LinkName.of("rel_on_source"))
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(Entity.builder()
                                        .name(EntityName.of("Non-existing"))
                                        .table(TableName.of("non_existing"))
                                        .pathSegment(PathSegmentName.of("non-existings"))
                                        .linkName(LinkName.of("non-existings"))
                                        .build())
                                .name(RelationName.of("name_on_target"))
                                .pathSegment(PathSegmentName.of("segment-on-target"))
                                .linkName(LinkName.of("rel_on_target"))
                                .build())
                        .targetReference(ColumnName.of("ref_on_source"))
                        .build());
        assertThrows(EntityDefinitionNotFoundException.class, builder::build);
    }

    @Test
    void application_searchFilterWithNonExistentRelation() {
        var entity = Entity.builder()
                .name(EntityName.of("testEntity"))
                .table(TableName.of("test_table"))
                .pathSegment(PathSegmentName.of("test-entities"))
                .linkName(LinkName.of("test-entities"))
                .primaryKey(SimpleAttribute.builder()
                        .name(AttributeName.of("id"))
                        .column(ColumnName.of("id"))
                        .type(Type.UUID)
                        .flag(ReadOnlyFlag.INSTANCE)
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("nonexistent.name"))
                        .attributePath(PropertyPath.of(RelationName.of("nonexistent"), AttributeName.of("name")))
                        .build())
                .build();

        var applicationBuilder = Application.builder()
                .name(ApplicationName.of("testApp"))
                .entity(entity);

        assertThrows(RelationNotFoundException.class, applicationBuilder::build);
    }

    @Test
    void application_searchFilterWithNonExistentAttribute() {
        var sourceEntity = Entity.builder()
                .name(EntityName.of("sourceEntity"))
                .table(TableName.of("source_table"))
                .pathSegment(PathSegmentName.of("source-entities"))
                .linkName(LinkName.of("source-entities"))
                .primaryKey(SimpleAttribute.builder()
                        .name(AttributeName.of("id"))
                        .column(ColumnName.of("id"))
                        .type(Type.UUID)
                        .flag(ReadOnlyFlag.INSTANCE)
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("target.nonexistent"))
                        .attributePath(PropertyPath.of(RelationName.of("target"), AttributeName.of("nonexistent")))
                        .build())
                .build();

        var targetEntity = Entity.builder()
                .name(EntityName.of("targetEntity"))
                .table(TableName.of("target_table"))
                .pathSegment(PathSegmentName.of("target-entities"))
                .linkName(LinkName.of("target-entities"))
                .primaryKey(SimpleAttribute.builder()
                        .name(AttributeName.of("id"))
                        .column(ColumnName.of("id"))
                        .type(Type.UUID)
                        .flag(ReadOnlyFlag.INSTANCE)
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("name"))
                        .column(ColumnName.of("name"))
                        .type(Type.TEXT)
                        .build())
                .build();

        var relation = ManyToOneRelation.builder()
                .sourceEndPoint(RelationEndPoint.builder()
                        .entity(sourceEntity)
                        .name(RelationName.of("target"))
                        .pathSegment(PathSegmentName.of("target"))
                        .linkName(LinkName.of("target"))
                        .build())
                .targetEndPoint(RelationEndPoint.builder()
                        .entity(targetEntity)
                        .name(RelationName.of("sources"))
                        .pathSegment(PathSegmentName.of("sources"))
                        .linkName(LinkName.of("sources"))
                        .build())
                .targetReference(ColumnName.of("target_id"))
                .build();

        var applicationBuilder = Application.builder()
                .name(ApplicationName.of("testApp"))
                .entity(sourceEntity)
                .entity(targetEntity)
                .relation(relation);

        assertThrows(AttributeNotFoundException.class, applicationBuilder::build);
    }

    @ParameterizedTest
    @CsvSource({
            "UUID", "LONG", "DOUBLE", "BOOLEAN", "DATETIME"
    })
    void application_searchFilterInvalidAttributeType(Type type) {
        var entity = Entity.builder()
                .name(EntityName.of("test"))
                .table(TableName.of("test"))
                .pathSegment(PathSegmentName.of("test"))
                .linkName(LinkName.of("test"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("test"))
                        .column(ColumnName.of("test"))
                        .type(type)
                        .build()
                )
                .searchFilter(
                        PrefixSearchFilter.builder().name(FilterName.of("filter~prefix"))
                                .attributePath(PropertyPath.of(AttributeName.of("test")))
                                .build()
                )
                .build();

        assertThrows(InvalidSearchFilterException.class, () -> {
            Application.builder()
                    .name(ApplicationName.of("test-app"))
                    .entity(entity)
                    .build();
        });
    }

    /**
     * Test if we can create the application we use for integration testing:
     * https://console.contentgrid.com/app-frontend/integration-test-do-not-touch
     */
    @Test
    void application_integration_testing() {

        var customerId = SimpleAttribute.builder().type(Type.UUID).name(AttributeName.of("id"))
                .column(ColumnName.of("id")).flag(ReadOnlyFlag.INSTANCE).build();
        var customerName = SimpleAttribute.builder().type(Type.TEXT)
                .name(AttributeName.of("name"))
                .column(ColumnName.of("name"))
                .description("the name of the customer")
                .build();
        var customerEmail = SimpleAttribute.builder().type(Type.TEXT)
                .name(AttributeName.of("email"))
                .column(ColumnName.of("email"))
                .description("the email of the customer")
                .constraint(Constraint.unique())
                .build();

        var customerPhone = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("phone_number"))
                .column(ColumnName.of("phone_number"))
                .description("phone number of the customer")
                .build();

        var customer = Entity.builder()
                .name(EntityName.of("customer"))
                .table(TableName.of("customer"))
                .pathSegment(PathSegmentName.of("customers"))
                .linkName(LinkName.of("customers"))
                .primaryKey(customerId)
                .attribute(customerName)
                .searchFilter(
                        PrefixSearchFilter.builder().attribute(customerName).name(FilterName.of("name~prefix")).build())
                .attribute(customerEmail)
                .attribute(customerPhone)
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("orders.order_number"))
                        .attributePath(PropertyPath.of(RelationName.of("orders"), AttributeName.of("order_number")))
                        .build())
                .build();

        var orderId = SimpleAttribute.builder().type(Type.UUID).name(AttributeName.of("id")).column(ColumnName.of("id"))
                .flag(ReadOnlyFlag.INSTANCE).build();

        var orderNumber = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("order_number"))
                .column(ColumnName.of("order_number"))
                .description("the order number")
                .constraint(Constraint.unique())
                .build();

        var orderTotalAmount = SimpleAttribute.builder().type(Type.DOUBLE).name(AttributeName.of("total_amount"))
                .column(ColumnName.of("total_amount"))
                .description("the total amount of the order")
                .build();

        var orderDocument = ContentAttribute.builder().name(AttributeName.of("document"))
                .pathSegment(PathSegmentName.of("document"))
                .linkName(LinkName.of("document"))
                .description("A file attachment representing the document associated with the order.")
                .idColumn(ColumnName.of("document__id"))
                .filenameColumn(ColumnName.of("document__filename"))
                .mimetypeColumn(ColumnName.of("document__mimetype"))
                .lengthColumn(ColumnName.of("document__length"))
                .build();

        var orderAudit = CompositeAttributeImpl.builder()
                .name(AttributeName.of("auditing"))
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("created_by"))
                        .flag(CreatorFlag.INSTANCE)
                        .idColumn(ColumnName.of("auditing__created_by_id"))
                        .namespaceColumn(ColumnName.of("auditing__created_by_ns"))
                        .usernameColumn(ColumnName.of("auditing__created_by_name"))
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("created_date"))
                        .column(ColumnName.of("auditing__created_date"))
                        .type(Type.DATETIME)
                        .flag(CreatedDateFlag.INSTANCE)
                        .build())
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("last_modified_by"))
                        .flag(ModifierFlag.INSTANCE)
                        .idColumn(ColumnName.of("auditing__last_modified_by_id"))
                        .namespaceColumn(ColumnName.of("auditing__last_modified_by_ns"))
                        .usernameColumn(ColumnName.of("auditing__last_modified_by_name"))
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("last_modified_date"))
                        .column(ColumnName.of("auditing__last_modified_date"))
                        .type(Type.DATETIME)
                        .flag(ModifiedDateFlag.INSTANCE)
                        .build())
                .build();

        var order = Entity.builder()
                .name(EntityName.of("order"))
                .description("Represents an order placed by a customer, consisting of various products.")
                .table(TableName.of("order"))
                .pathSegment(PathSegmentName.of("orders"))
                .linkName(LinkName.of("orders"))
                .primaryKey(orderId)
                .attribute(orderNumber)
                .searchFilter(
                        ExactSearchFilter.builder().attribute(orderNumber).name(FilterName.of("order_number")).build()
                )
                .attribute(orderTotalAmount)
                .attribute(orderDocument)
                .attribute(orderAudit)
                .searchFilter(PrefixSearchFilter.builder()
                        .name(FilterName.of("products.name"))
                        .attributePath(PropertyPath.of(RelationName.of("products"), AttributeName.of("name")))
                        .build())
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("products.category"))
                        .attributePath(PropertyPath.of(RelationName.of("products"), AttributeName.of("category")))
                        .build())
                .build();

        var productName = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("name"))
                .column(ColumnName.of("name"))
                .description("the name of the product")
                .build();

        var productCode = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("code"))
                .column(ColumnName.of("code"))
                .description("the code of the product")
                .constraint(Constraint.unique())
                .build();

        var productCategory = SimpleAttribute.builder().type(Type.TEXT).name(AttributeName.of("category"))
                .column(ColumnName.of("category"))
                .description("the category of the product")
                .constraint(Constraint.allowedValues(List.of("electronics", "clothing", "books")))
                .build();

        var product = Entity.builder()
                .name(EntityName.of("product"))
                .table(TableName.of("product"))
                .pathSegment(PathSegmentName.of("products"))
                .linkName(LinkName.of("products"))
                .primaryKey(SimpleAttribute.builder().type(Type.UUID).name(AttributeName.of("id")).column(ColumnName.of("id")).flag(ReadOnlyFlag.INSTANCE).build())
                .attribute(productName)
                .searchFilter(PrefixSearchFilter.builder().attribute(productName).name(FilterName.of("name")).build())
                .attribute(SimpleAttribute.builder().type(Type.DOUBLE).name(AttributeName.of("price")).column(ColumnName.of("price")).build())
                .attribute(productCode)
                .attribute(productCategory)
                .searchFilter(ExactSearchFilter.builder().attribute(productCategory).name(FilterName.of("category")).build())
                .build();

        var application = Application.builder()
                .name(ApplicationName.of("integration-test-do-not-touch"))
                .entity(customer)
                .entity(order)
                .relation(
                        OneToManyRelation.builder()
                                .sourceEndPoint(RelationEndPoint.builder().entity(customer).name(RelationName.of("orders"))
                                        .pathSegment(PathSegmentName.of("orders"))
                                        .linkName(LinkName.of("orders"))
                                        .description("Represents the orders placed by a customer.")
                                        .build())
                                .targetEndPoint(RelationEndPoint.builder().entity(order).build())
                                .sourceReference(ColumnName.of("_customer_id__orders"))
                                .build()
                )
                .entity(product)
                .relation(
                        ManyToManyRelation.builder()
                                .sourceEndPoint(RelationEndPoint.builder().entity(order).name(RelationName.of("products"))
                                        .pathSegment(PathSegmentName.of("products"))
                                        .linkName(LinkName.of("products"))
                                        .description("Represents the products in an order.")
                                        .build())
                                .targetEndPoint(RelationEndPoint.builder().entity(product).build())
                                .joinTable(TableName.of("order_product"))
                                .sourceReference(ColumnName.of("_order_id"))
                                .targetReference(ColumnName.of("_product_id"))
                                .build()
                )
                .build();

        assertEquals(customer, application.getEntityByName(EntityName.of("customer")).orElseThrow());
        var orderOutgoingRelations = application.getRelationsForSourceEntity(order);
        var orderIncomingRelations = application.getRelationsForTargetEntity(order);
        assertEquals(2, orderOutgoingRelations.size());
        assertEquals(2, orderIncomingRelations.size());
        assertTrue(orderIncomingRelations.stream()
                .allMatch(incomingRelation -> orderOutgoingRelations.contains(incomingRelation.inverse())));

    }

}