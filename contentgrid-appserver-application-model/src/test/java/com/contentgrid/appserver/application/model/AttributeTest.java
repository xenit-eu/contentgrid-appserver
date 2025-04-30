package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.CreatorFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifierFlag;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import java.util.List;
import java.util.Set;
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
    void eTagAttribute() {
        var attribute = SimpleAttribute.builder().name(AttributeName.of("attribute"))
                .column(ColumnName.of("column")).type(Type.LONG).flag(ETagFlag.builder().build()).build();

        assertEquals(Set.of(ETagFlag.builder().build()), attribute.getFlags());
    }

    @Test
    void eTag_invalidAttribute() {
        var builder = SimpleAttribute.builder().name(AttributeName.of("attribute"))
                .column(ColumnName.of("column")).type(Type.DATETIME).flag(ETagFlag.builder().build());

        assertThrows(InvalidFlagException.class, builder::build);
    }

    @Test
    void contentAttribute() {
        var attribute = ContentAttribute.builder()
                .name(AttributeName.of("attribute"))
                .pathSegment(PathSegmentName.of("segment"))
                .description("The pdf file of the entity")
                .idColumn(ColumnName.of("column__id"))
                .filenameColumn(ColumnName.of("column__filename"))
                .mimetypeColumn(ColumnName.of("column__mimetype"))
                .lengthColumn(ColumnName.of("column__length"))
                .build();

        assertEquals(AttributeName.of("attribute"), attribute.getName());
        assertEquals(PathSegmentName.of("segment"), attribute.getPathSegment());
        assertEquals("The pdf file of the entity", attribute.getDescription());
        assertEquals(AttributeName.of("id"), attribute.getId().getName());
        assertEquals(ColumnName.of("column__id"), ((SimpleAttribute) attribute.getId()).getColumn());
        assertEquals(AttributeName.of("filename"), attribute.getFilename().getName());
        assertEquals(ColumnName.of("column__filename"), ((SimpleAttribute) attribute.getFilename()).getColumn());
        assertEquals(AttributeName.of("mimetype"), attribute.getMimetype().getName());
        assertEquals(ColumnName.of("column__mimetype"), ((SimpleAttribute) attribute.getMimetype()).getColumn());
        assertEquals(AttributeName.of("length"), attribute.getLength().getName());
        assertEquals(ColumnName.of("column__length"), ((SimpleAttribute) attribute.getLength()).getColumn());
    }

    @Test
    void compositeAttribute_auditMetadata() {
        var attribute = CompositeAttributeImpl.builder()
                .name(AttributeName.of("auditing"))
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("created_by"))
                        .flag(CreatorFlag.builder().build())
                        .idColumn(ColumnName.of("auditing__created_by_id"))
                        .namespaceColumn(ColumnName.of("auditing__created_by_ns"))
                        .usernameColumn(ColumnName.of("auditing__created_by_name"))
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("created_date"))
                        .column(ColumnName.of("auditing__created_date"))
                        .type(Type.DATETIME)
                        .flag(CreatedDateFlag.builder().build())
                        .build())
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("last_modified_by"))
                        .idColumn(ColumnName.of("auditing__last_modified_by_id"))
                        .namespaceColumn(ColumnName.of("auditing__last_modified_by_ns"))
                        .usernameColumn(ColumnName.of("auditing__last_modified_by_name"))
                        .flag(ModifierFlag.builder().build())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("last_modified_date"))
                        .column(ColumnName.of("auditing__last_modified_date"))
                        .type(Type.DATETIME)
                        .flag(ModifiedDateFlag.builder().build())
                        .build())
                .build();

        assertEquals(4, attribute.getAttributes().size());

        var attributeNames = attribute.getAttributes().stream().map(Attribute::getName).toList();
        assertEquals(4, attributeNames.size());
        var createdBy = assertInstanceOf(UserAttribute.class, attribute.getAttributeByName(AttributeName.of("created_by")).orElseThrow());
        assertEquals(AttributeName.of("id"), createdBy.getId().getName());
        assertEquals(AttributeName.of("namespace"), createdBy.getNamespace().getName());
        assertEquals(AttributeName.of("name"), createdBy.getUsername().getName());
        assertInstanceOf(SimpleAttribute.class, attribute.getAttributeByName(AttributeName.of("created_date")).orElseThrow());
        var modifiedBy = assertInstanceOf(UserAttribute.class, attribute.getAttributeByName(AttributeName.of("last_modified_by")).orElseThrow());
        assertEquals(AttributeName.of("id"), modifiedBy.getId().getName());
        assertEquals(AttributeName.of("namespace"), modifiedBy.getNamespace().getName());
        assertEquals(AttributeName.of("name"), modifiedBy.getUsername().getName());
        assertInstanceOf(SimpleAttribute.class, attribute.getAttributeByName(AttributeName.of("last_modified_date")).orElseThrow());

        assertEquals(Set.of(CreatorFlag.builder().build()), attribute.getAttributeByName(AttributeName.of("created_by")).orElseThrow().getFlags());
        assertEquals(Set.of(CreatedDateFlag.builder().build()), attribute.getAttributeByName(AttributeName.of("created_date")).orElseThrow().getFlags());
        assertEquals(Set.of(ModifierFlag.builder().build()), attribute.getAttributeByName(AttributeName.of("last_modified_by")).orElseThrow().getFlags());
        assertEquals(Set.of(ModifiedDateFlag.builder().build()), attribute.getAttributeByName(AttributeName.of("last_modified_date")).orElseThrow().getFlags());

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