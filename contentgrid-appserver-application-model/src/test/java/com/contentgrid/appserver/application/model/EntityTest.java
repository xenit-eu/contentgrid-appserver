package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EntityTest {

    @Test
    void getAttributeByNameTest() {
        var entity = Entity.builder()
                .name("Entity")
                .table("entity")
                .attribute(Attribute.builder().name("attribute1").column("column1").type(Attribute.Type.TEXT).build())
                .attribute(Attribute.builder().name("attribute2").column("column2").type(Attribute.Type.TEXT).build())
                .build();

        assertEquals("attribute1", entity.getAttributeByName("attribute1").get().getName());
        assertEquals("column1", entity.getAttributeByName("attribute1").get().getColumn());
        assertEquals(Attribute.Type.TEXT, entity.getAttributeByName("attribute1").get().getType());

        var attributes = entity.getAttributes();
        var attribute3 = Attribute.builder().name("attribute3").column("column3").type(Attribute.Type.TEXT).build();

        // validate that the list of attributes is immutable
        assertThrows(
                UnsupportedOperationException.class,
                () -> attributes.add(attribute3)
        );

    }

}