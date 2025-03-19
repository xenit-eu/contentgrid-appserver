package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToOneRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    @Test
    void invoiceApplicationTest() {
        var invoice = Entity.builder()
                .name("Invoice")
                .table("invoice")
                .attribute(Attribute.builder().name("invoiceNumber").column("invoice_number").type(Type.TEXT).build())
                .attribute(Attribute.builder().name("amount").column("amount").type(Type.DOUBLE).build())
                .attribute(Attribute.builder().name("isPaid").column("is_paid").type(Type.BOOLEAN).build())
                .attribute(Attribute.builder().name("content").column("content").type(Type.CONTENT).build())
                .build();

        var customer = Entity.builder()
                .name("Customer")
                .table("customer")
                .attribute(Attribute.builder().name("name").column("name").type(Type.TEXT).build())
                .attribute(Attribute.builder().name("email").column("email").type(Type.TEXT).build())
                .build();

        var application = Application.builder()
                .name("invoiceApplication")
                .entity(invoice)
                .entity(customer)
                .relation(
                        ManyToOneRelation.builder()
                                .source(Relation.RelationEndPoint.builder().name("customer").entity(invoice).build())
                                .target(Relation.RelationEndPoint.builder().name("invoices").entity(customer).build())
                                .targetReference("customer_id")
                                .build()
                )
                .build();

        assertEquals(customer, application.getRelationForEntity(invoice, "customer").get().getTarget().getEntity());
    }

}