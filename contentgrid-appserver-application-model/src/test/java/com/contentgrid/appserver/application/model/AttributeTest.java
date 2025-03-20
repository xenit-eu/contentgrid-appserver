package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttributeTest {

    @Test
    void requiredAttribute() {
        var attribute = SimpleAttribute.builder().name(AttributeName.of("attribute")).column(ColumnName.of("column")).type(Type.TEXT).constraint(Constraint.required()).build();

        assertEquals(AttributeName.of("attribute"), attribute.getName());
        assertEquals(ColumnName.of("column"), attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getType());
        assertTrue(attribute.getConstraint(RequiredConstraint.class).isPresent());
        assertTrue(attribute.getConstraint(UniqueConstraint.class).isEmpty());
    }

    @Test
    void uniqueAttribute() {
        var attribute = SimpleAttribute.builder().name(AttributeName.of("attribute")).column(ColumnName.of("column")).type(Type.TEXT).constraint(Constraint.unique()).build();

        assertEquals(AttributeName.of("attribute"), attribute.getName());
        assertEquals(ColumnName.of("column"), attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getType());
        assertTrue(attribute.getConstraint(UniqueConstraint.class).isPresent());
        assertTrue(attribute.getConstraint(RequiredConstraint.class).isEmpty());
    }

    @Test
    void allowedValuesAttribute() {
        var attribute = SimpleAttribute.builder().name(AttributeName.of("attribute")).column(ColumnName.of("column")).type(Type.TEXT)
                .constraint(Constraint.allowedValues(List.of("test", "demo"))).build();

        assertEquals(AttributeName.of("attribute"), attribute.getName());
        assertEquals(ColumnName.of("column"), attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getType());
        assertTrue(attribute.getConstraint(UniqueConstraint.class).isEmpty());
        assertTrue(attribute.getConstraint(RequiredConstraint.class).isEmpty());
        assertTrue(attribute.getConstraint(AllowedValuesConstraint.class).isPresent());
        assertEquals(List.of("test", "demo"), attribute.getConstraint(AllowedValuesConstraint.class).orElseThrow().getValues());
    }
}