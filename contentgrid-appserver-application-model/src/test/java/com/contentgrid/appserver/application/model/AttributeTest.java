package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
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
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
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
    void contentAttribute_defaultFields() {
        var attribute = ContentAttribute.builder().name(AttributeName.of("attribute")).build();

        assertNull(attribute.getDescription());
        assertEquals(AttributeName.of("id"), attribute.getId().getName());
        assertEquals(ColumnName.of("attribute__id"), ((SimpleAttribute) attribute.getId()).getColumn());
        assertNull(attribute.getId().getDescription());
        assertEquals(AttributeName.of("filename"), attribute.getFilename().getName());
        assertEquals(ColumnName.of("attribute__filename"), ((SimpleAttribute) attribute.getFilename()).getColumn());
        assertNull(attribute.getFilename().getDescription());
        assertEquals(AttributeName.of("mimetype"), attribute.getMimetype().getName());
        assertEquals(ColumnName.of("attribute__mimetype"), ((SimpleAttribute) attribute.getMimetype()).getColumn());
        assertNull(attribute.getMimetype().getDescription());
        assertEquals(AttributeName.of("length"), attribute.getLength().getName());
        assertEquals(ColumnName.of("attribute__length"), ((SimpleAttribute) attribute.getLength()).getColumn());
        assertNull(attribute.getLength().getDescription());
    }

    @Test
    void contentAttribute_customFields() {
        var attribute = ContentAttribute.builder().name(AttributeName.of("attribute"))
                .description("The pdf file of the entity")
                .id(SimpleAttribute.builder()
                        .type(Type.UUID)
                        .name(AttributeName.of("attribute_id"))
                        .column(ColumnName.of("column__id"))
                        .description("The content id of the attribute")
                        .build())
                .filename(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("attribute_filename"))
                        .column(ColumnName.of("column__filename"))
                        .description("The content filename of the attribute")
                        .build())
                .mimetype(SimpleAttribute.builder()
                        .type(Type.TEXT)
                        .name(AttributeName.of("attribute_mimetype"))
                        .column(ColumnName.of("column__mimetype"))
                        .description("The content mimetype of the attribute")
                        .build())
                .length(SimpleAttribute.builder()
                        .type(Type.LONG)
                        .name(AttributeName.of("attribute_length"))
                        .column(ColumnName.of("column__length"))
                        .description("The content length of the attribute")
                        .build())
                .build();

        assertEquals("The pdf file of the entity", attribute.getDescription());
        assertEquals(AttributeName.of("attribute_id"), attribute.getId().getName());
        assertEquals(ColumnName.of("column__id"), ((SimpleAttribute) attribute.getId()).getColumn());
        assertEquals(Type.UUID, ((SimpleAttribute) attribute.getId()).getType());
        assertEquals("The content id of the attribute", attribute.getId().getDescription());
        assertEquals(AttributeName.of("attribute_filename"), attribute.getFilename().getName());
        assertEquals(ColumnName.of("column__filename"), ((SimpleAttribute) attribute.getFilename()).getColumn());
        assertEquals("The content filename of the attribute", attribute.getFilename().getDescription());
        assertEquals(AttributeName.of("attribute_mimetype"), attribute.getMimetype().getName());
        assertEquals(ColumnName.of("column__mimetype"), ((SimpleAttribute) attribute.getMimetype()).getColumn());
        assertEquals("The content mimetype of the attribute", attribute.getMimetype().getDescription());
        assertEquals(AttributeName.of("attribute_length"), attribute.getLength().getName());
        assertEquals(ColumnName.of("column__length"), ((SimpleAttribute) attribute.getLength()).getColumn());
        assertEquals("The content length of the attribute", attribute.getLength().getDescription());
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
    void compositeAttribute_auditMetadata_full() {
        // Fully write out created_by, simplify last_modified_by
        var attribute = CompositeAttribute.builder()
                .name(AttributeName.of("auditing"))
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("created_by"))
                        .flag(CreatorFlag.builder().build())
                        .id(SimpleAttribute.builder()
                                .name(AttributeName.of("id"))
                                .column(ColumnName.of("auditing__created_by_id"))
                                .type(Type.TEXT)
                                .build())
                        .namespace(SimpleAttribute.builder()
                                .name(AttributeName.of("namespace"))
                                .column(ColumnName.of("auditing__created_by_ns"))
                                .type(Type.TEXT)
                                .build())
                        .username(SimpleAttribute.builder()
                                .name(AttributeName.of("name"))
                                .column(ColumnName.of("auditing__created_by_name"))
                                .type(Type.TEXT)
                                .build())
                        .build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("created_date"))
                        .column(ColumnName.of("auditing__created_date"))
                        .type(Type.DATETIME)
                        .flag(CreatedDateFlag.builder().build())
                        .build())
                .attribute(UserAttribute.builder()
                        .name(AttributeName.of("last_modified_by"))
                        .columnPrefix(ColumnName.of("auditing__last_modified_by_"))
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