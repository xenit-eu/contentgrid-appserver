package com.contentgrid.appserver.domain.data.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.LongDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NulByteValidatorTest {

    private final NulByteValidator validator = new NulByteValidator();

    private static final SimpleAttribute TEXT_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("description"))
            .column(ColumnName.of("description"))
            .type(Type.TEXT)
            .build();

    private static final SimpleAttribute LONG_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("count"))
            .column(ColumnName.of("count"))
            .type(Type.LONG)
            .build();

    private static final SimpleAttribute TAGS_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("tags"))
            .column(ColumnName.of("tags"))
            .type(Type.TEXT_SET)
            .build();

    private static final SimpleAttributePath PATH = new SimpleAttributePath(AttributeName.of("description"));

    @Test
    void textWithNulByteThrows() {
        assertThrows(InvalidDataFormatException.class,
                () -> validator.validate(PATH, TEXT_ATTR, new StringDataEntry("foo\u0000bar")));
    }

    @Test
    void textWithoutNulBytePasses() {
        assertDoesNotThrow(() -> validator.validate(PATH, TEXT_ATTR, new StringDataEntry("foobar")));
    }

    @Test
    void textSetWithNulByteElementThrows() {
        assertThrows(InvalidDataFormatException.class,
                () -> validator.validate(PATH, TAGS_ATTR, new ListDataEntry(List.of(
                        new StringDataEntry("foo"), new StringDataEntry("bar\u0000baz")))));
    }

    @Test
    void textSetWithoutNulBytePasses() {
        assertDoesNotThrow(() -> validator.validate(PATH, TAGS_ATTR, new ListDataEntry(List.of(
                new StringDataEntry("foo"), new StringDataEntry("bar")))));
    }

    @Test
    void nonTextAttributeIsIgnored() {
        assertDoesNotThrow(() -> validator.validate(PATH, LONG_ATTR, new LongDataEntry(123L)));
    }

    @Test
    void nullValueIsIgnored() {
        assertDoesNotThrow(() -> validator.validate(PATH, TEXT_ATTR, NullDataEntry.INSTANCE));
    }
}
