package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.Attribute.Type;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import org.junit.jupiter.api.Test;

class EntityTest {

    private static final Attribute PRIMARY_KEY = Attribute.builder().name("id").column("id").type(Type.UUID).build();
    private static final Attribute ATTRIBUTE1 = Attribute.builder().name("attribute1").column("column1").type(Type.TEXT).build();
    private static final Attribute ATTRIBUTE2 = Attribute.builder().name("attribute2").column("column2").type(Type.BOOLEAN).build();
    private static final SearchFilter FILTER1 = PrefixSearchFilter.builder().name("filter1").attribute(ATTRIBUTE1).build();
    private static final SearchFilter FILTER2 = ExactSearchFilter.builder().name("filter2").attribute(ATTRIBUTE2).build();

    @Test
    void entityTest() {
        var entity = Entity.builder()
                .name("entity")
                .table("table")
                .primaryKey(PRIMARY_KEY)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .build();

        // getAttributeByName
        assertEquals("attribute1", entity.getAttributeByName("attribute1").orElseThrow().getName());
        assertEquals("column1", entity.getAttributeByName("attribute1").orElseThrow().getColumn());
        assertEquals(Attribute.Type.TEXT, entity.getAttributeByName("attribute1").orElseThrow().getType());

        // getAttributeByColumn
        assertEquals("attribute1", entity.getAttributeByColumn("column1").orElseThrow().getName());
        assertEquals("column1", entity.getAttributeByColumn("column1").orElseThrow().getColumn());
        assertEquals(Attribute.Type.TEXT, entity.getAttributeByColumn("column1").orElseThrow().getType());

        // getFilterByName
        var filter = entity.getFilterByName("filter1").orElseThrow();
        assertEquals("filter1", filter.getName());
        assertInstanceOf(PrefixSearchFilter.class, filter);
        assertEquals("attribute1", ((AttributeSearchFilter) filter).getAttribute().getName());
        assertEquals("column1", ((AttributeSearchFilter) filter).getAttribute().getColumn());

        // Can not use column name for finding attribute by name or vice versa
        assertTrue(entity.getAttributeByName("column1").isEmpty());
        assertTrue(entity.getAttributeByColumn("attribute1").isEmpty());

        var attributes = entity.getAttributes();
        var attribute3 = Attribute.builder().name("attribute3").column("column3").type(Attribute.Type.TEXT).build();

        // validate that the list of attributes is immutable
        assertThrows(
                UnsupportedOperationException.class,
                () -> attributes.add(attribute3)
        );

        var filters = entity.getSearchFilters();
        var filter3 = ExactSearchFilter.builder().name("filter3").attribute(ATTRIBUTE1).build();

        // validate that the list of search filters is immutable
        assertThrows(
                UnsupportedOperationException.class,
                () -> filters.add(filter3)
        );

    }

    @Test
    void entity_defaultPrimaryKey() {
        var entity = Entity.builder()
                .name("entity")
                .table("table")
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .build();

        assertEquals(PRIMARY_KEY, entity.getPrimaryKey());
    }

    @Test
    void entity_differentPrimaryKey() {
        var primaryKey = Attribute.builder().name("entity-id").column("entity_id").type(Type.LONG).build();
        var entity = Entity.builder()
                .name("entity")
                .table("table")
                .primaryKey(primaryKey)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .build();

        assertEquals(primaryKey, entity.getPrimaryKey());
    }

    @Test
    void entity_duplicateAttributeName() {
        // normal attribute
        var duplicate1 = Attribute.builder().name(ATTRIBUTE1.getName()).column("other_column").type(Type.TEXT).build();
        var builder1 = Entity.builder()
                .name("entity")
                .table("table")
                .attribute(ATTRIBUTE1)
                .attribute(duplicate1)
                .searchFilter(FILTER1);
        assertThrows(IllegalArgumentException.class, builder1::build);

        // primary key attribute
        var duplicate2 = Attribute.builder().name("id").column("other_column").type(Type.TEXT).build();
        var builder2 = Entity.builder()
                .name("entity")
                .table("table")
                .attribute(ATTRIBUTE1)
                .attribute(duplicate2)
                .searchFilter(FILTER1);
        assertThrows(IllegalArgumentException.class, builder2::build);
    }

    @Test
    void entity_duplicateColumnName() {
        // normal column
        var duplicate1 = Attribute.builder().name("other_name").column(ATTRIBUTE1.getColumn()).type(Type.TEXT).build();
        var builder1 = Entity.builder()
                .name("entity")
                .table("table")
                .attribute(ATTRIBUTE1)
                .attribute(duplicate1)
                .searchFilter(FILTER1);
        assertThrows(IllegalArgumentException.class, builder1::build);

        // primary key column
        var duplicate2 = Attribute.builder().name("other_name").column("id").type(Type.TEXT).build();
        var builder2 = Entity.builder()
                .name("entity")
                .table("table")
                .attribute(ATTRIBUTE1)
                .attribute(duplicate2)
                .searchFilter(FILTER1);
        assertThrows(IllegalArgumentException.class, builder2::build);
    }

    @Test
    void entity_duplicateFilterName() {
        var duplicate = ExactSearchFilter.builder().name(FILTER1.getName()).attribute(ATTRIBUTE2).build();
        var builder = Entity.builder()
                .name("entity")
                .table("table")
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(duplicate);
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    void entity_filterOnMissingAttribute() {
        var builder = Entity.builder()
                .name("entity")
                .table("table")
                .attribute(ATTRIBUTE1)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2); // on missing ATTRIBUTE2
        assertThrows(IllegalArgumentException.class, builder::build);
    }

}