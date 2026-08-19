package com.contentgrid.appserver.domain.data.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.propertypath.SimpleAttributePath;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.domain.data.DataEntry.ListDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.NullDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.PlainDataEntry;
import com.contentgrid.appserver.domain.data.DataEntry.StringDataEntry;
import com.contentgrid.appserver.domain.data.InvalidDataFormatException;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TextSetValidatorTest {

    private final TextSetValidator validator = new TextSetValidator();

    private static final SimpleAttribute TAGS_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("tags"))
            .column(ColumnName.of("tags"))
            .type(Type.TEXT_SET)
            .build();

    private static final SimpleAttribute TEXT_ATTR = SimpleAttribute.builder()
            .name(AttributeName.of("description"))
            .column(ColumnName.of("description"))
            .type(Type.TEXT)
            .build();

    private static final SimpleAttributePath PATH = new SimpleAttributePath(AttributeName.of("tags"));

    private static ListDataEntry listOf(String... values) {
        return new ListDataEntry(Arrays.stream(values).<PlainDataEntry>map(StringDataEntry::new).toList());
    }

    @Test
    void distinctElementsPass() {
        assertDoesNotThrow(() -> validator.validate(PATH, TAGS_ATTR, listOf("urgent", "vip")));
    }

    @Test
    void duplicateElementsThrow() {
        var exception = assertThrows(DuplicateElementInvalidDataException.class,
                () -> validator.validate(PATH, TAGS_ATTR, listOf("urgent", "vip", "urgent")));
        assertEquals("urgent", exception.getDuplicateValue());
    }

    @Test
    void duplicatesAreComparedNfkcNormalized() {
        // The same value in composed (NFC) and decomposed (NFD) encoding is a duplicate
        assertThrows(DuplicateElementInvalidDataException.class,
                () -> validator.validate(PATH, TAGS_ATTR, listOf("café", "café")));
    }

    @Test
    void elementCountIsCapped() {
        var atLimit = IntStream.rangeClosed(1, TextSetValidator.MAX_ELEMENTS)
                .mapToObj("value-%d"::formatted)
                .toArray(String[]::new);
        assertDoesNotThrow(() -> validator.validate(PATH, TAGS_ATTR, listOf(atLimit)));

        var overLimit = IntStream.rangeClosed(0, TextSetValidator.MAX_ELEMENTS)
                .mapToObj("value-%d"::formatted)
                .toArray(String[]::new);
        assertThrows(InvalidDataFormatException.class,
                () -> validator.validate(PATH, TAGS_ATTR, listOf(overLimit)));
    }

    @Test
    void otherAttributeTypesAreIgnored() {
        assertDoesNotThrow(() -> validator.validate(PATH, TEXT_ATTR, new StringDataEntry("urgent")));
    }

    @Test
    void nullValueIsIgnored() {
        assertDoesNotThrow(() -> validator.validate(PATH, TAGS_ATTR, NullDataEntry.INSTANCE));
    }
}
