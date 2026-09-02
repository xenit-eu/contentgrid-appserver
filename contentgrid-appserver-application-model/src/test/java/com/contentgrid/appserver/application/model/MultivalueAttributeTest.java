package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.CreatedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ModifiedDateFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.exceptions.InvalidAttributeTypeException;
import com.contentgrid.appserver.application.model.exceptions.InvalidConstraintException;
import com.contentgrid.appserver.application.model.exceptions.InvalidFlagException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;

class MultivalueAttributeTest {

    private static MultivalueAttribute.MultivalueAttributeBuilder builder() {
        return MultivalueAttribute.builder().name(AttributeName.of("attribute")).column(ColumnName.of("column"))
                .itemType(Type.TEXT);
    }

    @Test
    void multivalueAttribute() {
        var attribute = builder().build();

        assertEquals(AttributeName.of("attribute"), attribute.getName());
        assertEquals(ColumnName.of("column"), attribute.getColumn());
        assertEquals(Type.TEXT, attribute.getItemType());
        assertEquals(List.of(), attribute.getConstraints());
        assertEquals(List.of(ColumnName.of("column")), attribute.getColumns());
        assertEquals(Set.of(), attribute.getFlags());
        assertEquals("attribute", attribute.getTranslations(Locale.ROOT).getName());
    }

    @Test
    void multivalueAttributeWithAllowedValues() {
        var attribute = builder().constraint(Constraint.allowedValues(List.of("test", "demo"))).build();

        assertTrue(attribute.hasConstraint(AllowedValuesConstraint.class));
        assertFalse(attribute.hasConstraint(RequiredConstraint.class));
        assertEquals(List.of("test", "demo"),
                attribute.getConstraint(AllowedValuesConstraint.class).orElseThrow().getValues());
    }

    @ParameterizedTest
    @EnumSource(value = Type.class, names = "TEXT", mode = Mode.EXCLUDE)
    void multivalueAttribute_invalidItemTypes(Type itemType) {
        var builder = MultivalueAttribute.builder().name(AttributeName.of("attribute"))
                .column(ColumnName.of("column")).itemType(itemType);
        assertThrows(InvalidAttributeTypeException.class, builder::build);
    }

    static Stream<Constraint> multivalueAttribute_invalidConstraints() {
        return Stream.of(Constraint.required(), Constraint.unique(), Constraint.pattern("[0-9]+"));
    }

    @ParameterizedTest
    @MethodSource
    void multivalueAttribute_invalidConstraints(Constraint constraint) {
        var builder = builder().constraint(constraint);
        assertThrows(InvalidConstraintException.class, builder::build);
    }

    @Test
    void multivalueAttribute_cannotBeNestedInComposite() {
        var composite = CompositeAttributeImpl.builder().name(AttributeName.of("meta")).attribute(builder().build());
        assertThrows(InvalidAttributeTypeException.class, composite::build);
    }

    @Test
    void multivalueAttribute_invalidFlags() {
        var withETag = builder().flag(ETagFlag.INSTANCE);
        assertThrows(InvalidFlagException.class, withETag::build);

        var withCreatedDate = builder().flag(CreatedDateFlag.INSTANCE);
        assertThrows(InvalidFlagException.class, withCreatedDate::build);

        var withModifiedDate = builder().flag(ModifiedDateFlag.INSTANCE);
        assertThrows(InvalidFlagException.class, withModifiedDate::build);
    }

    @Test
    void readOnlyMultivalueAttribute() {
        var attribute = builder().flag(ReadOnlyFlag.INSTANCE).build();

        assertTrue(attribute.isReadOnly());
        assertFalse(attribute.isIgnored());
    }

    @Test
    void multivalueAttributeWithDescription() {
        var attribute = builder().description("A set of tags").build();

        assertEquals("A set of tags", attribute.getTranslations(Locale.ROOT).getDescription());
    }

    @Test
    void translationsAreExcludedFromEquality() {
        var attribute = builder().build();
        var sameWithDescription = builder().description("A set of tags").build();
        var differentColumn = MultivalueAttribute.builder().name(AttributeName.of("attribute"))
                .column(ColumnName.of("other_column")).itemType(Type.TEXT).build();

        assertEquals(attribute, sameWithDescription);
        assertNotEquals(attribute, differentColumn);
    }
}
