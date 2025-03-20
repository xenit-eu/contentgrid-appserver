package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.EntityNotFoundException;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    private static final Entity INVOICE = Entity.builder()
            .name("Invoice")
            .table("invoice")
            .attribute(Attribute.builder().name("invoiceNumber").column("invoice_number").type(Type.TEXT).build())
            .attribute(Attribute.builder().name("amount").column("amount").type(Type.DOUBLE).build())
            .attribute(Attribute.builder().name("isPaid").column("is_paid").type(Type.BOOLEAN).build())
            .attribute(Attribute.builder().name("content").column("content").type(Type.CONTENT).build())
            .build();

    private static final Entity CUSTOMER = Entity.builder()
            .name("Customer")
            .table("customer")
            .attribute(Attribute.builder().name("name").column("name").type(Type.TEXT).build())
            .attribute(Attribute.builder().name("email").column("email").type(Type.TEXT).build())
            .build();

    private static final Relation MANY_TO_ONE = ManyToOneRelation.builder()
            .source(Relation.RelationEndPoint.builder().name("customer").entity(INVOICE).build())
            .target(Relation.RelationEndPoint.builder().name("invoices").entity(CUSTOMER).build())
            .targetReference("customer_id")
            .build();

    @Test
    void invoiceApplicationTest() {
        var application = Application.builder()
                .name("invoiceApplication")
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .build();

        assertEquals(CUSTOMER, application.getRelationForEntity(INVOICE, "customer").get().getTarget().getEntity());
        assertEquals(INVOICE, application.getEntityByName("Invoice").orElseThrow());
        assertEquals(INVOICE, application.getEntityByTable("invoice").orElseThrow());
        assertEquals(INVOICE, application.getRelationForEntity("Customer", "invoices").orElseThrow().getSource().getEntity());
    }

    @Test
    void application_duplicateEntityName() {
        var builder = Application.builder()
                .name("duplicateEntityApplication")
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name(INVOICE.getName())
                        .table("other_table")
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateTableName() {
        var builder = Application.builder()
                .name("duplicateTableApplication")
                .entity(INVOICE)
                .entity(Entity.builder()
                        .name("other_entity")
                        .table(INVOICE.getTable())
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateRelationSource() {
        // relation1.source = relation2.source
        var builder = Application.builder()
                .name("duplicateRelationApplication")
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .relation(ManyToManyRelation.builder()
                        .source(MANY_TO_ONE.getSource())
                        .target(RelationEndPoint.builder().name("name_on_target").entity(CUSTOMER).build())
                        .joinTable("join_table")
                        .sourceReference("ref_on_source")
                        .targetReference("ref_on_target")
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateRelationTarget() {
        // relation1.target = relation2.target
        var builder = Application.builder()
                .name("duplicateRelationApplication")
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .relation(OneToOneRelation.builder()
                        .source(RelationEndPoint.builder().name("name_on_source").entity(INVOICE).build())
                        .target(MANY_TO_ONE.getTarget())
                        .targetReference("ref_on_target")
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_duplicateRelationSourceWithTarget() {
        // relation1.source = relation2.target
        var builder = Application.builder()
                .name("duplicateRelationApplication")
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(MANY_TO_ONE)
                .relation(OneToManyRelation.builder()
                        .source(RelationEndPoint.builder().name("name_on_source").entity(CUSTOMER).build())
                        .target(MANY_TO_ONE.getSource())
                        .sourceReference("ref_on_source")
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void application_nonExistingSourceEntity() {
        var builder = Application.builder()
                .name("nonExistingEntityApplication")
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(ManyToOneRelation.builder()
                        .source(RelationEndPoint.builder()
                                .entity(Entity.builder().name("Non-existing").table("non_existing").build())
                                .name("name_on_source")
                                .build())
                        .target(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name("name_on_target")
                                .build())
                        .targetReference("ref_on_target")
                        .build());
        assertThrows(EntityNotFoundException.class, builder::build);
    }

    @Test
    void application_nonExistingTargetEntity() {
        var builder = Application.builder()
                .name("nonExistingEntityApplication")
                .entity(INVOICE)
                .entity(CUSTOMER)
                .relation(ManyToOneRelation.builder()
                        .source(RelationEndPoint.builder()
                                .entity(INVOICE)
                                .name("name_on_source")
                                .build())
                        .target(RelationEndPoint.builder()
                                .entity(Entity.builder().name("Non-existing").table("non_existing").build())
                                .name("name_on_target")
                                .build())
                        .targetReference("ref_on_target")
                        .build());
        assertThrows(EntityNotFoundException.class, builder::build);
    }

}