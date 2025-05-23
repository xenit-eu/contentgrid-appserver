package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.exceptions.InvalidAttributeTypeException;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkRel;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EntityTest {

    private static final SimpleAttribute PRIMARY_KEY = SimpleAttribute.builder().name(AttributeName.of("id")).column(
            ColumnName.of("id")).type(Type.UUID).build();
    private static final SimpleAttribute ATTRIBUTE1 = SimpleAttribute.builder().name(AttributeName.of("attribute1")).column(ColumnName.of("column1")).type(Type.TEXT).build();
    private static final SimpleAttribute ATTRIBUTE2 = SimpleAttribute.builder().name(AttributeName.of("attribute2")).column(ColumnName.of("column2")).type(Type.BOOLEAN).build();
    private static final SearchFilter FILTER1 = PrefixSearchFilter.builder().name(FilterName.of("filter1")).attribute(ATTRIBUTE1).build();
    private static final SearchFilter FILTER2 = ExactSearchFilter.builder().name(FilterName.of("filter2")).attribute(ATTRIBUTE2).build();

    @Test
    void entityTest() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .description("entity description")
                .primaryKey(PRIMARY_KEY)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .build();

        assertEquals(EntityName.of("entity"), entity.getName());
        assertEquals(PathSegmentName.of("segment"), entity.getPathSegment());
        assertEquals(LinkRel.parse("d:collection"), entity.getCollectionLinkRel());
        assertEquals(LinkRel.parse("d:item"), entity.getItemLinkRel());
        assertEquals(TableName.of("table"), entity.getTable());
        assertEquals("entity description", entity.getDescription());

        // getAttributeByName
        var foundAttribute = entity.getAttributeByName(AttributeName.of("attribute1")).orElseThrow();
        assertEquals(AttributeName.of("attribute1"), foundAttribute.getName());
        assertEquals(List.of(ColumnName.of("column1")), foundAttribute.getColumns());
        assertInstanceOf(SimpleAttribute.class, foundAttribute);
        assertEquals(Type.TEXT, ((SimpleAttribute) foundAttribute).getType());

        // getFilterByName
        var filter = entity.getFilterByName(FilterName.of("filter1")).orElseThrow();
        assertEquals(FilterName.of("filter1"), filter.getName());
        assertInstanceOf(PrefixSearchFilter.class, filter);
        assertEquals(AttributeName.of("attribute1"), ((AttributeSearchFilter) filter).getAttribute().getName());
        assertEquals(ColumnName.of("column1"), ((AttributeSearchFilter) filter).getAttribute().getColumn());

        // Can not use column name or filter name for finding attribute by name
        assertTrue(entity.getAttributeByName(AttributeName.of("column1")).isEmpty());
        assertTrue(entity.getAttributeByName(AttributeName.of("filter1")).isEmpty());

        var attributes = entity.getAttributes();
        var attribute3 = SimpleAttribute.builder().name(AttributeName.of("attribute3")).column(ColumnName.of("column3")).type(SimpleAttribute.Type.TEXT).build();

        // validate that the list of attributes is immutable
        assertThrows(
                UnsupportedOperationException.class,
                () -> attributes.add(attribute3)
        );

        var filters = entity.getSearchFilters();
        var filter3 = ExactSearchFilter.builder().name(FilterName.of("filter3")).attribute(ATTRIBUTE1).build();

        // validate that the list of search filters is immutable
        assertThrows(
                UnsupportedOperationException.class,
                () -> filters.add(filter3)
        );

    }

    @Test
    void entity_defaultPrimaryKey() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .build();

        assertEquals(PRIMARY_KEY, entity.getPrimaryKey());
    }

    @Test
    void entity_differentPrimaryKey() {
        var primaryKey = SimpleAttribute.builder().name(AttributeName.of("entity-id")).column(ColumnName.of("entity_id")).type(Type.UUID).build();
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .primaryKey(primaryKey)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .build();

        assertEquals(primaryKey, entity.getPrimaryKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEXT", "LONG", "DOUBLE", "BOOLEAN", "DATETIME"})
    void entity_invalidPrimaryKey(Type type) {
        var primaryKey = SimpleAttribute.builder().name(AttributeName.of("entity-id")).column(ColumnName.of("entity_id")).type(type).build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .primaryKey(primaryKey)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2);

        assertThrows(InvalidAttributeTypeException.class, builder::build);
    }

    @Test
    void entity_duplicateAttributeName() {
        // normal attribute
        var duplicate1 = SimpleAttribute.builder().name(ATTRIBUTE1.getName()).column(ColumnName.of("other_column")).type(Type.TEXT).build();
        var builder1 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate1)
                .searchFilter(FILTER1);
        assertThrows(DuplicateElementException.class, builder1::build);

        // primary key attribute
        var duplicate2 = SimpleAttribute.builder().name(AttributeName.of("id")).column(ColumnName.of("other_column")).type(Type.TEXT).build();
        var builder2 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate2)
                .searchFilter(FILTER1);
        assertThrows(DuplicateElementException.class, builder2::build);
    }

    @Test
    void entity_duplicateColumnName() {
        // normal column
        var duplicate1 = SimpleAttribute.builder().name(AttributeName.of("other_name")).column(ATTRIBUTE1.getColumn()).type(Type.TEXT).build();
        var builder1 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate1)
                .searchFilter(FILTER1);
        assertThrows(DuplicateElementException.class, builder1::build);

        // primary key column
        var duplicate2 = SimpleAttribute.builder().name(AttributeName.of("other_name")).column(ColumnName.of("id")).type(Type.TEXT).build();
        var builder2 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate2)
                .searchFilter(FILTER1);
        assertThrows(DuplicateElementException.class, builder2::build);
    }

    @Test
    void entity_duplicateFilterName() {
        var duplicate = ExactSearchFilter.builder().name(FILTER1.getName()).attribute(ATTRIBUTE2).build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(duplicate);
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void entity_filterOnMissingAttribute() {
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .collectionLinkRel(LinkRel.parse("d:collection"))
                .itemLinkRel(LinkRel.parse("d:item"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2); // on missing ATTRIBUTE2
        assertThrows(InvalidArgumentModelException.class, builder::build);
    }

}