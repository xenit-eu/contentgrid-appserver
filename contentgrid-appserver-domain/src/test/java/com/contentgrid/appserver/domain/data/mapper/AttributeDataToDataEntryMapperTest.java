package com.contentgrid.appserver.domain.data.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttributeImpl;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.domain.data.DataEntry.MapDataEntry;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttributeDataToDataEntryMapperTest {

    private static final SimpleAttribute LABEL = SimpleAttribute.builder()
            .name(AttributeName.of("label"))
            .column(ColumnName.of("nested__label"))
            .type(Type.TEXT)
            .build();

    private static final SimpleAttribute SECRET = SimpleAttribute.builder()
            .name(AttributeName.of("secret"))
            .column(ColumnName.of("nested__secret"))
            .type(Type.TEXT)
            .flag(IgnoredFlag.INSTANCE)
            .build();

    private static final ContentAttribute CONTENT = ContentAttribute.builder()
            .name(AttributeName.of("content"))
            .idColumn(ColumnName.of("nested__content__id"))
            .filenameColumn(ColumnName.of("nested__content__filename"))
            .mimetypeColumn(ColumnName.of("nested__content__mimetype"))
            .lengthColumn(ColumnName.of("nested__content__length"))
            .flag(IgnoredFlag.INSTANCE)
            .build();

    private static final CompositeAttribute NESTED = CompositeAttributeImpl.builder()
            .name(AttributeName.of("nested"))
            .attribute(LABEL)
            .attribute(CONTENT)
            .attribute(SECRET)
            .build();

    private final AttributeDataToDataEntryMapper mapper = new AttributeDataToDataEntryMapper();

    private static CompositeAttributeData nestedData(String contentId) {
        return CompositeAttributeData.builder()
                .name(NESTED.getName())
                .attribute(new SimpleAttributeData<>(LABEL.getName(), "hello"))
                .attribute(CompositeAttributeData.builder()
                        .name(CONTENT.getName())
                        .attribute(new SimpleAttributeData<>(AttributeName.of("id"), contentId))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("filename"), "file.pdf"))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("mimetype"), "application/pdf"))
                        .attribute(new SimpleAttributeData<>(AttributeName.of("length"), 42L))
                        .build())
                .attribute(new SimpleAttributeData<>(SECRET.getName(), "x"))
                .build();
    }

    @Test
    void compositeWithIgnoredAttributes_omitsAttributes() {
        var result = mapper.mapAttribute(NESTED, Optional.of(nestedData("content-id")));

        assertThat(result).isInstanceOfSatisfying(MapDataEntry.class, map ->
                assertThat(map.getItems())
                        .containsOnlyKeys("label")
        );
    }
}