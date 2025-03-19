package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttributeTest {

    @Test
    void requiredAttribute() {
        var attribute = Attribute.builder().name("attribute").column("column").type(Type.TEXT).constraint(Constraint.required()).build();

        assertEquals("attribute", attribute.getName());
        assertEquals("column", attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getType());
        assertTrue(attribute.getConstraint(RequiredConstraint.class).isPresent());
        assertTrue(attribute.getConstraint(UniqueConstraint.class).isEmpty());
    }

    @Test
    void uniqueAttribute() {
        var attribute = Attribute.builder().name("attribute").column("column").type(Type.TEXT).constraint(Constraint.unique()).build();

        assertEquals("attribute", attribute.getName());
        assertEquals("column", attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getType());
        assertTrue(attribute.getConstraint(UniqueConstraint.class).isPresent());
        assertTrue(attribute.getConstraint(RequiredConstraint.class).isEmpty());
    }

    @Test
    void allowedValuesAttribute() {
        var attribute = Attribute.builder().name("attribute").column("column").type(Type.TEXT)
                .constraint(Constraint.allowedValues(List.of("test", "demo"))).build();

        assertEquals("attribute", attribute.getName());
        assertEquals("column", attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getType());
        assertTrue(attribute.getConstraint(UniqueConstraint.class).isEmpty());
        assertTrue(attribute.getConstraint(RequiredConstraint.class).isEmpty());
        assertTrue(attribute.getConstraint(AllowedValuesConstraint.class).isPresent());
        assertEquals(List.of("test", "demo"), attribute.getConstraint(AllowedValuesConstraint.class).orElseThrow().getValues());
    }
}