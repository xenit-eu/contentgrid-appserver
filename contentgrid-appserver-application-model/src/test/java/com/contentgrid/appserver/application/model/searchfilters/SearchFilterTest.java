package com.contentgrid.appserver.application.model.searchfilters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.InvalidSearchFilterException;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
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
        var exactSearchFilter = ExactSearchFilter.builder()
                .name(FilterName.of("filter")).attribute(getAttribute(type)).build();
        assertEquals(FilterName.of("filter"), exactSearchFilter.getName());
        assertEquals(PropertyPath.of(AttributeName.of("name")), exactSearchFilter.getAttributePath());
        assertEquals(type, exactSearchFilter.getAttributeType());
    }

    @Test
    void searchFilterWithPath() {
        var exactSearchFilter = ExactSearchFilter.builder()
                .name(FilterName.of("filter")).attributePath(PropertyPath.of(AttributeName.of("foo"), AttributeName.of("bar")))
                .attributeType(Type.TEXT).build();
        assertEquals(FilterName.of("filter"), exactSearchFilter.getName());
        assertEquals(PropertyPath.of(AttributeName.of("foo"), AttributeName.of("bar")), exactSearchFilter.getAttributePath());
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
        var prefixSearchFilter = PrefixSearchFilter.builder()
                .name(FilterName.of("filter~prefix")).attribute(getAttribute(Type.TEXT)).build();
        assertEquals(FilterName.of("filter~prefix"), prefixSearchFilter.getName());
        assertEquals(PropertyPath.of(AttributeName.of("name")), prefixSearchFilter.getAttributePath());
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