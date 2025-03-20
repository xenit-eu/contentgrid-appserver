package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.TableName;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final Entity INVOICE = Entity.builder()
            .name(EntityName.of("Invoice"))
            .table(TableName.of("invoice"))
            .attribute(SimpleAttribute.builder().name(AttributeName.of("invoiceNumber")).column(ColumnName.of("invoice_number")).type(Type.TEXT).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("amount")).column(ColumnName.of("amount")).type(Type.DOUBLE).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("isPaid")).column(ColumnName.of("is_paid")).type(Type.BOOLEAN).build())
            .attribute(ContentAttribute.builder().name(AttributeName.of("content")).build())
            .build();

    private static final Entity CUSTOMER = Entity.builder()
            .name(EntityName.of("Customer"))
            .table(TableName.of("customer"))
            .attribute(SimpleAttribute.builder().name(AttributeName.of("name")).column(ColumnName.of("name")).type(Type.TEXT).build())
            .attribute(SimpleAttribute.builder().name(AttributeName.of("email")).column(ColumnName.of("email")).type(Type.TEXT).build())
            .build();

    private static final Relation MANY_TO_ONE = ManyToOneRelation.builder()
            .source(Relation.RelationEndPoint.builder().name(RelationName.of("customer")).entity(INVOICE).build())
            .target(Relation.RelationEndPoint.builder().name(RelationName.of("invoices")).entity(CUSTOMER).build())
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

        assertEquals(CUSTOMER, application.getRelationForEntity(INVOICE, RelationName.of("customer")).orElseThrow().getTarget().getEntity());
        assertEquals(INVOICE, application.getEntityByName(EntityName.of("Invoice")).orElseThrow());
        assertEquals(INVOICE, application.getRelationForEntity(EntityName.of("Customer"), RelationName.of("invoices")).orElseThrow().getSource().getEntity());
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
                        .target(RelationEndPoint.builder().name(RelationName.of("name_on_target")).entity(CUSTOMER).build())
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
                        .source(RelationEndPoint.builder().name(RelationName.of("name_on_source")).entity(INVOICE).build())
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
                        .source(RelationEndPoint.builder().name(RelationName.of("name_on_source")).entity(CUSTOMER).build())
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
                                .entity(Entity.builder().name(EntityName.of("Non-existing")).table(TableName.of("non_existing")).build())
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
                                .entity(Entity.builder().name(EntityName.of("Non-existing")).table(TableName.of("non_existing")).build())
                                .name(RelationName.of("name_on_target"))
                                .build())
                        .targetReference(ColumnName.of("ref_on_target"))
                        .build());
        assertThrows(EntityNotFoundException.class, builder::build);
    }

}