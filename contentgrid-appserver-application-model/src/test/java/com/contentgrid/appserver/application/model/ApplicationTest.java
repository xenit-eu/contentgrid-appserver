package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.relations.ManyToOne;
import com.contentgrid.appserver.application.model.relations.Relation;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    @Test
    void invoiceApplicationTest() {
        var invoice = Entity.builder()
                .name("Invoice")
                .attribute(Attribute.builder().name("invoiceNumber").type(Type.TEXT).build())
                .attribute(Attribute.builder().name("amount").type(Type.DOUBLE).build())
                .attribute(Attribute.builder().name("isPaid").type(Type.BOOLEAN).build())
                .attribute(Attribute.builder().name("content").type(Type.CONTENT).build())
                .build();

        var customer = Entity.builder()
                .name("Customer")
                .attribute(Attribute.builder().name("name").type(Type.TEXT).build())
                .attribute(Attribute.builder().name("email").type(Type.TEXT).build())
                .build();

        var application = Application.builder()
                .name("invoiceApplication")
                .entity(invoice)
                .entity(customer)
                .relation(
                        ManyToOne.builder()
                                .source(Relation.RelationEndPoint.builder().name("customer").entity(invoice).build())
                                .target(Relation.RelationEndPoint.builder().name("invoices").entity(customer).build())
                                .targetReference("customer_id")
                                .build()
                )
                .build();

        assertEquals(customer, application.getRelationForEntity(invoice, "customer").get().getTarget().getEntity());
    }

}