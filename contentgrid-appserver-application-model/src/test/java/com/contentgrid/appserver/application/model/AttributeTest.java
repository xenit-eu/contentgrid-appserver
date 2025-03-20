package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.ManagedType;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
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

    @Test
    void simpleAttribute() {
        var attribute = SimpleAttribute.builder().name(AttributeName.of("attribute")).column(ColumnName.of("column")).type(Type.TEXT).build();

        assertEquals(AttributeName.of("attribute"), attribute.getName());
        assertEquals(ColumnName.of("column"), attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getType());
        assertEquals(List.of(), attribute.getConstraints());
        assertEquals(List.of(ColumnName.of("column")), attribute.getColumns());
    }

    @Test
    void contentAttribute_defaultFields() {
        var attribute = ContentAttribute.builder().name(AttributeName.of("attribute")).build();

        assertEquals(AttributeName.of("id"), attribute.getId().getName());
        assertEquals(ColumnName.of("attribute__id"), ((SimpleAttribute) attribute.getId()).getColumn());
        assertEquals(AttributeName.of("filename"), attribute.getFilename().getName());
        assertEquals(ColumnName.of("attribute__filename"), ((SimpleAttribute) attribute.getFilename()).getColumn());
        assertEquals(AttributeName.of("mimetype"), attribute.getMimetype().getName());
        assertEquals(ColumnName.of("attribute__mimetype"), ((SimpleAttribute) attribute.getMimetype()).getColumn());
        assertEquals(AttributeName.of("length"), attribute.getLength().getName());
        assertEquals(ColumnName.of("attribute__length"), ((SimpleAttribute) attribute.getLength()).getColumn());
    }

    @Test
    void contentAttribute_customFields() {
        var attribute = ContentAttribute.builder().name(AttributeName.of("attribute"))
                .id(SimpleAttribute.builder()
                        .type(Type.UUID)
                        .name(AttributeName.of("attribute_id"))
                        .column(ColumnName.of("column__id"))
                        .build())
                .filename(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("attribute_filename"))
                        .column(ColumnName.of("column__filename"))
                        .build())
                .mimetype(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("attribute_mimetype"))
                        .column(ColumnName.of("column__mimetype"))
                        .build())
                .length(SimpleAttribute.builder()
                        .type(Type.LONG)
                        .name(AttributeName.of("attribute_length"))
                        .column(ColumnName.of("column__length"))
                        .build())
                .build();

        assertEquals(AttributeName.of("attribute_id"), attribute.getId().getName());
        assertEquals(ColumnName.of("column__id"), ((SimpleAttribute) attribute.getId()).getColumn());
        assertEquals(Type.UUID, ((SimpleAttribute) attribute.getId()).getType());
        assertEquals(AttributeName.of("attribute_filename"), attribute.getFilename().getName());
        assertEquals(ColumnName.of("column__filename"), ((SimpleAttribute) attribute.getFilename()).getColumn());
        assertEquals(AttributeName.of("attribute_mimetype"), attribute.getMimetype().getName());
        assertEquals(ColumnName.of("column__mimetype"), ((SimpleAttribute) attribute.getMimetype()).getColumn());
        assertEquals(AttributeName.of("attribute_length"), attribute.getLength().getName());
        assertEquals(ColumnName.of("column__length"), ((SimpleAttribute) attribute.getLength()).getColumn());
    }

    @Test
    void contentAttribute_duplicateFieldName() {
        var builder = ContentAttribute.builder().name(AttributeName.of("attribute"))
                .id(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("duplicate"))
                        .column(ColumnName.of("column__id"))
                        .build())
                .filename(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("attribute_filename"))
                        .column(ColumnName.of("column__filename"))
                        .build())
                .mimetype(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("duplicate"))
                        .column(ColumnName.of("column__mimetype"))
                        .build())
                .length(SimpleAttribute.builder()
                        .type(Type.LONG)
                        .name(AttributeName.of("attribute_length"))
                        .column(ColumnName.of("column__length"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void contentAttribute_duplicateDefaultFieldName() {
        var builder = ContentAttribute.builder().name(AttributeName.of("attribute"))
                .mimetype(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("filename")) // mimetype is called filename too
                        .column(ColumnName.of("column__mimetype"))
                        .build());
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void compositeAttribute_auditMetadata() {
        var attribute = CompositeAttribute.builder()
                .name(AttributeName.of("auditing"))
                .attribute(CompositeAttribute.builder()
                        .name(AttributeName.of("created_by"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("id"))
                                .column(ColumnName.of("auditing__created_by_id"))
                                .type(Type.TEXT)
                                .managedType(ManagedType.CREATOR_ID)
                                .build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("namespace"))
                                .column(ColumnName.of("auditing__created_by_ns"))
                                .type(Type.TEXT)
                                .managedType(ManagedType.CREATOR_NAME)
                                .build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("name"))
                                .column(ColumnName.of("auditing__created_by_name"))
                                .type(Type.TEXT)
                                .managedType(ManagedType.CREATOR_NAMESPACE)
                                .build())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("created_date"))
                        .column(ColumnName.of("auditing__created_date"))
                        .type(Type.DATETIME)
                        .managedType(ManagedType.CREATED_DATE)
                        .build())
                .attribute(CompositeAttribute.builder()
                        .name(AttributeName.of("last_modified_by"))
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("id"))
                                .column(ColumnName.of("auditing__last_modified_by_id"))
                                .type(Type.TEXT)
                                .managedType(ManagedType.MODIFIER_ID)
                                .build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("namespace"))
                                .column(ColumnName.of("auditing__last_modified_by_ns"))
                                .type(Type.TEXT)
                                .managedType(ManagedType.MODIFIER_NAMESPACE)
                                .build())
                        .attribute(SimpleAttribute.builder()
                                .name(AttributeName.of("name"))
                                .column(ColumnName.of("auditing__last_modified_by_name"))
                                .type(Type.TEXT)
                                .managedType(ManagedType.MODIFIER_NAME)
                                .build())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("last_modified_date"))
                        .column(ColumnName.of("auditing__last_modified_date"))
                        .type(Type.DATETIME)
                        .managedType(ManagedType.MODIFIED_DATE)
                        .build())
                .build();

        assertEquals(4, attribute.getAttributes().size());

        var attributeNames = attribute.getAttributes().stream().map(Attribute::getName).toList();
        assertTrue(attributeNames.contains(AttributeName.of("created_by")));
        assertTrue(attributeNames.contains(AttributeName.of("created_date")));
        assertTrue(attributeNames.contains(AttributeName.of("last_modified_by")));
        assertTrue(attributeNames.contains(AttributeName.of("last_modified_date")));

        var columnNames = attribute.getColumns();
        assertTrue(columnNames.contains(ColumnName.of("auditing__created_by_id")));
        assertTrue(columnNames.contains(ColumnName.of("auditing__created_by_ns")));
        assertTrue(columnNames.contains(ColumnName.of("auditing__created_by_name")));
        assertTrue(columnNames.contains(ColumnName.of("auditing__created_date")));
        assertTrue(columnNames.contains(ColumnName.of("auditing__last_modified_by_id")));
        assertTrue(columnNames.contains(ColumnName.of("auditing__last_modified_by_ns")));
        assertTrue(columnNames.contains(ColumnName.of("auditing__last_modified_by_name")));
        assertTrue(columnNames.contains(ColumnName.of("auditing__last_modified_date")));
    }
}