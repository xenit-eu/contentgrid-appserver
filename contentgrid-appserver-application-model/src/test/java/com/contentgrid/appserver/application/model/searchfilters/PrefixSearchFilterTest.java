package com.contentgrid.appserver.application.model.searchfilters;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.Attribute;
import com.contentgrid.appserver.application.model.Attribute.Type;
import org.junit.jupiter.api.Test;

class PrefixSearchFilterTest {

    @Test
    void testGetName() {
        PrefixSearchFilter prefixSearchFilter = PrefixSearchFilter.builder().name("name~prefix").attribute(
                Attribute.builder().type(Type.TEXT).name("hello").column("hello").build()).build();
        assertEquals("name~prefix", prefixSearchFilter.getName());
    }

}