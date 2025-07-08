package com.contentgrid.appserver.application.model;

import static org.junit.jupiter.api.Assertions.*;

import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.exceptions.DuplicateElementException;
import com.contentgrid.appserver.application.model.exceptions.InvalidArgumentModelException;
import com.contentgrid.appserver.application.model.exceptions.InvalidAttributeTypeException;
import com.contentgrid.appserver.application.model.searchfilters.ExactSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.PrefixSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.SearchFilter;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.SortableName;
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
    private static final ContentAttribute CONTENT1 = ContentAttribute.builder()
            .name(AttributeName.of("content1"))
            .pathSegment(PathSegmentName.of("content1"))
            .linkName(LinkName.of("content1"))
            .idColumn(ColumnName.of("content1__id"))
            .filenameColumn(ColumnName.of("content1__filename"))
            .mimetypeColumn(ColumnName.of("content1__mimetype"))
            .lengthColumn(ColumnName.of("content1__length"))
            .build();
    private static final ContentAttribute CONTENT2 = ContentAttribute.builder()
            .name(AttributeName.of("content2"))
            .pathSegment(PathSegmentName.of("content2"))
            .linkName(LinkName.of("content2"))
            .idColumn(ColumnName.of("content2__id"))
            .filenameColumn(ColumnName.of("content2__filename"))
            .mimetypeColumn(ColumnName.of("content2__mimetype"))
            .lengthColumn(ColumnName.of("content2__length"))
            .build();
    private static final CompositeAttribute COMPOSITE = CompositeAttributeImpl.builder()
            .name(AttributeName.of("composite"))
            .attribute(CONTENT2)
            .build();
    private static final SearchFilter FILTER1 = PrefixSearchFilter.builder().name(FilterName.of("filter1")).attribute(ATTRIBUTE1).build();
    private static final SearchFilter FILTER2 = ExactSearchFilter.builder().name(FilterName.of("filter2")).attribute(ATTRIBUTE2).build();
    private static final SortableField SORTABLE1 = SortableField.builder().name(SortableName.of("sortable1")).propertyPath(PropertyPath.of(ATTRIBUTE1.getName())).build();

    @Test
    void entityTest() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .description("entity description")
                .primaryKey(PRIMARY_KEY)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .attribute(CONTENT1)
                .attribute(COMPOSITE)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .sortableField(SORTABLE1)
                .build();

        assertEquals(EntityName.of("entity"), entity.getName());
        assertEquals(PathSegmentName.of("segment"), entity.getPathSegment());
        assertEquals(LinkName.of("link"), entity.getLinkName());
        assertEquals(TableName.of("table"), entity.getTable());
        assertEquals("entity description", entity.getDescription());

        // getAttributeByName
        var foundAttribute = entity.getAttributeByName(AttributeName.of("attribute1")).orElseThrow();
        assertEquals(AttributeName.of("attribute1"), foundAttribute.getName());
        assertEquals(List.of(ColumnName.of("column1")), foundAttribute.getColumns());
        var simpleAttribute = assertInstanceOf(SimpleAttribute.class, foundAttribute);
        assertEquals(Type.TEXT, simpleAttribute.getType());

        // getFilterByName
        var filter = entity.getFilterByName(FilterName.of("filter1")).orElseThrow();
        assertEquals(FilterName.of("filter1"), filter.getName());
        var attrSearchFilter = assertInstanceOf(PrefixSearchFilter.class, filter);
        assertEquals(AttributeName.of("attribute1"), attrSearchFilter.getAttributePath().getFirst());
        assertEquals(Type.TEXT, attrSearchFilter.getAttributeType());

        // getContentByPathSegment
        var content = entity.getContentByPathSegment(PathSegmentName.of("content2")).orElseThrow();
        assertEquals(AttributeName.of("content2"), content.getName());
        assertEquals(LinkName.of("content2"), content.getLinkName());

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

        var sortables = entity.getSortableFields();
        var sortable2 = SortableField.builder().name(SortableName.of("sortable2")).propertyPath(PropertyPath.of(ATTRIBUTE2.getName())).build();

        // validate that the list of sortable fields is immutable
        assertThrows(
                UnsupportedOperationException.class,
                () -> sortables.add(sortable2)
        );


        var contentAttributes = entity.getContentAttributes();
        var content3 = ContentAttribute.builder()
                .name(AttributeName.of("content3"))
                .pathSegment(PathSegmentName.of("content3"))
                .linkName(LinkName.of("content3"))
                .idColumn(ColumnName.of("content3__id"))
                .filenameColumn(ColumnName.of("content3__filename"))
                .mimetypeColumn(ColumnName.of("content3__mimetype"))
                .lengthColumn(ColumnName.of("content3__length"))
                .build();

        // validate that list of content attributes is immutable
        assertThrows(
                UnsupportedOperationException.class,
                () -> contentAttributes.add(content3)
        );

    }

    @Test
    void entity_defaultPrimaryKey() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .sortableField(SORTABLE1)
                .build();

        assertEquals(PRIMARY_KEY, entity.getPrimaryKey());
    }

    @Test
    void entity_differentPrimaryKey() {
        var primaryKey = SimpleAttribute.builder().name(AttributeName.of("entity-id")).column(ColumnName.of("entity_id")).type(Type.UUID).build();
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .primaryKey(primaryKey)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .sortableField(SORTABLE1)
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
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .primaryKey(primaryKey)
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2)
                .sortableField(SORTABLE1);

        assertThrows(InvalidAttributeTypeException.class, builder::build);
    }

    @Test
    void entity_duplicateAttributeName() {
        // normal attribute
        var duplicate1 = SimpleAttribute.builder().name(ATTRIBUTE1.getName()).column(ColumnName.of("other_column")).type(Type.TEXT).build();
        var builder1 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate1)
                .searchFilter(FILTER1)
                .sortableField(SORTABLE1);
        assertThrows(DuplicateElementException.class, builder1::build);

        // primary key attribute
        var duplicate2 = SimpleAttribute.builder().name(AttributeName.of("id")).column(ColumnName.of("other_column")).type(Type.TEXT).build();
        var builder2 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate2)
                .searchFilter(FILTER1)
                .sortableField(SORTABLE1);
        assertThrows(DuplicateElementException.class, builder2::build);
    }

    @Test
    void entity_duplicateColumnName() {
        // normal column
        var duplicate1 = SimpleAttribute.builder().name(AttributeName.of("other_name")).column(ATTRIBUTE1.getColumn()).type(Type.TEXT).build();
        var builder1 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate1)
                .searchFilter(FILTER1)
                .sortableField(SORTABLE1);
        assertThrows(DuplicateElementException.class, builder1::build);

        // primary key column
        var duplicate2 = SimpleAttribute.builder().name(AttributeName.of("other_name")).column(ColumnName.of("id")).type(Type.TEXT).build();
        var builder2 = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(duplicate2)
                .searchFilter(FILTER1)
                .sortableField(SORTABLE1);
        assertThrows(DuplicateElementException.class, builder2::build);
    }

    @Test
    void entity_duplicateFilterName() {
        var duplicate = ExactSearchFilter.builder().name(FILTER1.getName()).attribute(ATTRIBUTE2).build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .searchFilter(duplicate);
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void entity_filterOnMissingAttribute() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .searchFilter(FILTER1)
                .searchFilter(FILTER2) // on missing ATTRIBUTE2
                .build();

        var applicationBuilder = Application.builder()
                .name(ApplicationName.of("testApp"))
                .entity(entity);

        assertThrows(InvalidArgumentModelException.class, applicationBuilder::build);
    }

    @Test
    void entity_filterWithWrongType() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("filter"))
                        .attributePath(PropertyPath.of(ATTRIBUTE1.getName()))
                        .attributeType(Type.BOOLEAN)
                        .build()
                )
                .build();

        var applicationBuilder = Application.builder()
                .name(ApplicationName.of("testApp"))
                .entity(entity);

        assertThrows(InvalidArgumentModelException.class, applicationBuilder::build);
    }

    @Test
    void entity_filterOnComposite() {
        Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(COMPOSITE)
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("filter"))
                        .attributePath(PropertyPath.of(COMPOSITE.getName(), CONTENT2.getName(), AttributeName.of("filename")))
                        .attributeType(Type.TEXT)
                        .build()
                ).build();
    }

    @Test
    void entity_filterOnCompositePointsToSimple() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("filter"))
                        .attributePath(PropertyPath.of(ATTRIBUTE1.getName(), AttributeName.of("foo")))
                        .attributeType(Type.TEXT)
                        .build()
                )
                .build();

        var applicationBuilder = Application.builder()
                .name(ApplicationName.of("testApp"))
                .entity(entity);

        assertThrows(InvalidArgumentModelException.class, applicationBuilder::build);
    }

    @Test
    void entity_filterOnSimplePointsToComposite() {
        var entity = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(COMPOSITE)
                .searchFilter(ExactSearchFilter.builder()
                        .name(FilterName.of("filter"))
                        .attributePath(PropertyPath.of(COMPOSITE.getName()))
                        .attributeType(Type.TEXT)
                        .build()
                )
                .build();

        var applicationBuilder = Application.builder()
                .name(ApplicationName.of("testApp"))
                .entity(entity);

        assertThrows(InvalidArgumentModelException.class, applicationBuilder::build);
    }

    @Test
    void entity_duplicateContentPathSegment() {
        var duplicate = ContentAttribute.builder()
                .name(AttributeName.of("content3"))
                .pathSegment(CONTENT2.getPathSegment())
                .linkName(LinkName.of("content3"))
                .idColumn(ColumnName.of("content3__id"))
                .filenameColumn(ColumnName.of("content3__filename"))
                .mimetypeColumn(ColumnName.of("content3__mimetype"))
                .lengthColumn(ColumnName.of("content3__length"))
                .build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(CONTENT1)
                .attribute(COMPOSITE)
                .attribute(duplicate);
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void entity_duplicateContentLinkName() {
        var duplicate = ContentAttribute.builder()
                .name(AttributeName.of("content3"))
                .pathSegment(PathSegmentName.of("content3"))
                .linkName(CONTENT2.getLinkName())
                .idColumn(ColumnName.of("content3__id"))
                .filenameColumn(ColumnName.of("content3__filename"))
                .mimetypeColumn(ColumnName.of("content3__mimetype"))
                .lengthColumn(ColumnName.of("content3__length"))
                .build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(CONTENT1)
                .attribute(COMPOSITE)
                .attribute(duplicate);
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void entity_duplicateSortableName() {
        var duplicate = SortableField.builder().name(SORTABLE1.getName()).propertyPath(PropertyPath.of(ATTRIBUTE2.getName())).build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(ATTRIBUTE2)
                .searchFilter(FILTER1)
                .sortableField(SORTABLE1)
                .sortableField(duplicate);
        assertThrows(DuplicateElementException.class, builder::build);
    }

    @Test
    void entity_sortableOnMissingAttribute() {
        var sortable2 = SortableField.builder().name(SortableName.of("sortable2")).propertyPath(PropertyPath.of(ATTRIBUTE2.getName())).build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                // No attribute 2 here
                .searchFilter(FILTER1)
                .sortableField(SORTABLE1)
                .sortableField(sortable2);
        assertThrows(InvalidArgumentModelException.class, builder::build);
    }

    @Test
    void entity_sortableOnCompositeAttribute() {
        var sortableComposite = SortableField.builder().name(SortableName.of("sortable2"))
                .propertyPath(PropertyPath.of(COMPOSITE.getName(), CONTENT2.getName())).build();
        var builder = Entity.builder()
                .name(EntityName.of("entity"))
                .pathSegment(PathSegmentName.of("segment"))
                .linkName(LinkName.of("link"))
                .table(TableName.of("table"))
                .attribute(ATTRIBUTE1)
                .attribute(COMPOSITE)
                .searchFilter(FILTER1)
                .sortableField(SORTABLE1)
                .sortableField(sortableComposite);
        assertThrows(InvalidArgumentModelException.class, builder::build);
    }
}