package com.contentgrid.appserver.application.model.searchfilters;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchFilterTest {

    static Attribute getAttribute(Type type) {
        return Attribute.builder().type(type).name("name").column("column").build();
    }

    @ParameterizedTest
    @CsvSource({
            "TEXT", "UUID", "LONG", "DOUBLE", "BOOLEAN", "DATETIME"
    })
    void exactSearchFilter(Type type) {
        var exactSearchFilter = ExactSearchFilter.builder()
                .name("filter").attribute(getAttribute(type)).build();
        assertEquals("filter", exactSearchFilter.getName());
    }

    @ParameterizedTest
    @CsvSource({
            "CONTENT", "AUDIT_METADATA"
    })
    void exactSearchFilter_invalidType(Type type) {
        var builder = ExactSearchFilter.builder().name("filter").attribute(getAttribute(type));
        assertThrows(InvalidSearchFilterException.class, builder::build);
    }

    @Test
    void prefixSearchFilter() {
        var prefixSearchFilter = PrefixSearchFilter.builder()
                .name("filter~prefix").attribute(getAttribute(Type.TEXT)).build();
        assertEquals("filter~prefix", prefixSearchFilter.getName());
    }

    @ParameterizedTest
    @CsvSource({
            "UUID", "LONG", "DOUBLE", "BOOLEAN", "DATETIME", "CONTENT", "AUDIT_METADATA"
    })
    void prefixSearchFilter_invalidType(Type type) {
        var builder = PrefixSearchFilter.builder().name("filter~prefix").attribute(getAttribute(type));
        assertThrows(InvalidSearchFilterException.class, builder::build);
    }

}