package com.contentgrid.appserver.application.model.searchfilters;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.FilterName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchFilterTest {

    static SimpleAttribute getAttribute(Type type) {
        return SimpleAttribute.builder().type(type).name(AttributeName.of("name")).column(ColumnName.of("column")).build();
    }

    @ParameterizedTest
    @CsvSource({
            "TEXT", "UUID", "LONG", "DOUBLE", "BOOLEAN", "DATETIME"
    })
    void exactSearchFilter(Type type) {
        var attribute = getAttribute(type);
        var exactSearchFilter = ExactSearchFilter.builder()
                .name(FilterName.of("filter")).attribute(attribute).build();
        assertEquals(FilterName.of("filter"), exactSearchFilter.getName());

        var defaultFilter = ExactSearchFilter.builder().attribute(attribute).build();
        assertEquals(FilterName.of("name"), defaultFilter.getName());
    }

    @ParameterizedTest
    @CsvSource({})
    @Disabled("All types are supported")
    void exactSearchFilter_invalidType(Type type) {
        var builder = ExactSearchFilter.builder().name(FilterName.of("filter")).attribute(getAttribute(type));
        assertThrows(InvalidSearchFilterException.class, builder::build);
    }

    @Test
    void prefixSearchFilter() {
        var attribute = getAttribute(Type.TEXT);
        var prefixSearchFilter = PrefixSearchFilter.builder()
                .name(FilterName.of("filter~prefix")).attribute(attribute).build();
        assertEquals(FilterName.of("filter~prefix"), prefixSearchFilter.getName());

        var defaultFilter = PrefixSearchFilter.builder().attribute(attribute).build();
        assertEquals(FilterName.of("name~prefix"), defaultFilter.getName());
    }

    @ParameterizedTest
    @CsvSource({
            "UUID", "LONG", "DOUBLE", "BOOLEAN", "DATETIME"
    })
    void prefixSearchFilter_invalidType(Type type) {
        var builder = PrefixSearchFilter.builder().name(FilterName.of("filter~prefix")).attribute(getAttribute(type));
        assertThrows(InvalidSearchFilterException.class, builder::build);
    }

}