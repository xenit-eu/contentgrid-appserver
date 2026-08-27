package com.contentgrid.appserver.query.engine.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.MultivalueAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.TableName;
import com.contentgrid.appserver.domain.values.EntityId;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.IllegalInputDataException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MultivalueAttributeJooqTest {

    private static final MultivalueAttribute TAGS = MultivalueAttribute.builder()
            .name(AttributeName.of("tags"))
            .column(ColumnName.of("tags"))
            .itemType(Type.TEXT)
            .build();

    private static final Entity DOCUMENT = Entity.builder()
            .name(EntityName.of("document"))
            .table(TableName.of("document"))
            .pathSegment(PathSegmentName.of("documents"))
            .linkName(LinkName.of("documents"))
            .primaryKey(SimpleAttribute.builder()
                    .name(AttributeName.of("id"))
                    .column(ColumnName.of("id"))
                    .type(Type.UUID)
                    .flag(ReadOnlyFlag.INSTANCE)
                    .build())
            .attribute(TAGS)
            .build();

    private static final Application APPLICATION = Application.builder()
            .name(ApplicationName.of("MultivalueAttributeJooqTest"))
            .entity(DOCUMENT)
            .build();

    @Test
    void resolveFieldRendersNonNullableTextArrayColumn() {
        var field = JOOQUtils.resolveField(TAGS);

        assertEquals("tags", field.getName());
        assertTrue(field.getDataType().isArray());
        assertFalse(field.getDataType().nullable());
    }

    @Test
    void resolveAttributeFieldsIncludesTheMultivalueColumn() {
        var fields = JOOQUtils.resolveAttributeFields(DOCUMENT);

        assertThat(fields).anySatisfy(field -> assertEquals("tags", field.getName()));
    }

    @Test
    void entityDataConverterBindsListAsStringArray() {
        var entityData = EntityData.builder()
                .name(DOCUMENT.getName())
                .id(EntityId.of(UUID.randomUUID()))
                .attribute(SimpleAttributeData.builder()
                        .name(AttributeName.of("tags"))
                        .value(List.of("a", "b"))
                        .build())
                .build();

        var pairs = EntityDataConverter.convert(entityData, DOCUMENT);

        assertThat(pairs).singleElement().satisfies(pair -> {
            assertEquals("tags", pair.field().getName());
            assertThat(pair.value()).isEqualTo(new String[] {"a", "b"});
        });
    }

    @Test
    void entityDataConverterRejectsNonListValue() {
        var entityData = EntityData.builder()
                .name(DOCUMENT.getName())
                .id(EntityId.of(UUID.randomUUID()))
                .attribute(SimpleAttributeData.builder()
                        .name(AttributeName.of("tags"))
                        .value("urgent")
                        .build())
                .build();

        assertThrows(IllegalInputDataException.class, () -> EntityDataConverter.convert(entityData, DOCUMENT));
    }

    @Test
    void entityDataMapperConvertsStringArrayToList() {
        var data = EntityDataMapper.from(TAGS, Map.of("tags", (Object) new String[] {"a", "b"}));

        assertEquals(AttributeName.of("tags"), data.getName());
        assertEquals(List.of("a", "b"), data.getValue());
    }

    @Test
    void entityDataMapperRejectsNonArrayValue() {
        assertThrows(IllegalStateException.class, () -> EntityDataMapper.from(TAGS, Map.of("tags", 42)));
    }
}
