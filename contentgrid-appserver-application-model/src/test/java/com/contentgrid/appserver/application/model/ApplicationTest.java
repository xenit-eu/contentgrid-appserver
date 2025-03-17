package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.Attribute.Type;
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
                        Relation.builder()
                                .source(Relation.RelationEndPoint.builder().name("customer").entity(invoice).cardinality(Relation.Cardinality.ONE).build())
                                .target(Relation.RelationEndPoint.builder().name("invoices").entity(customer).cardinality(Relation.Cardinality.MANY).build())
                                .build()
                )
                .build();

        assertEquals(customer, application.getRelationForEntity(invoice, "customer").get().getTarget().getEntity());
    }

}