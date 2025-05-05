package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.util.Map;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EntityDataMapper {

    public EntityData from(@NonNull Entity entity, Map<String, Object> data) {
        var builder = EntityData.builder().name(entity.getName());
        for (var attribute : entity.getAllAttributes()) {
            builder.attribute(attribute.getName(), from(attribute, data));
        }
        return builder.build();
    }

    public AttributeData from(@NonNull Attribute attribute, Map<String, Object> data) {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> from(simpleAttribute, data);
            case CompositeAttribute compositeAttribute -> from(compositeAttribute, data);
        };
    }

    public SimpleAttributeData<?> from(@NonNull SimpleAttribute attribute, Map<String, Object> data) {
        return SimpleAttributeData.builder()
                .name(attribute.getName())
                .value(data.get(attribute.getColumn().getValue()))
                .build();
    }

    public CompositeAttributeData from(@NonNull CompositeAttribute attribute, Map<String, Object> data) {
        var builder = CompositeAttributeData.builder().name(attribute.getName());
        for (var child : attribute.getAttributes()) {
            builder.attribute(child.getName(), from(child, data));
        }
        return builder.build();
    }

}
