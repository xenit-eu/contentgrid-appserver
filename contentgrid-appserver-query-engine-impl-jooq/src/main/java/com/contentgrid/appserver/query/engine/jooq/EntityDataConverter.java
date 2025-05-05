package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import com.contentgrid.appserver.query.engine.api.exception.InvalidDataException;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.jooq.Field;

@UtilityClass
public class EntityDataConverter {

    public Map<Field<Object>, Object> convert(EntityData data, Entity entity) {
        var result = new HashMap<Field<Object>, Object>();
        for (var attributeData : data.getAttributes()) {
            var attribute = entity.getAttributeByName(attributeData.getName())
                    .orElseThrow(() -> new InvalidDataException("Attribute '%s' not found on entity '%s'"
                            .formatted(attributeData.getName(), entity.getName())));
            var converted = convert(attributeData, attribute);
            result.putAll(converted);
        }
        return result;
    }

    public Map<Field<Object>, Object> convert(AttributeData data, Attribute attribute) {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> {
                if (data instanceof SimpleAttributeData<?> simpleAttributeData) {
                    yield convert(simpleAttributeData, simpleAttribute);
                } else {
                    throw new InvalidDataException("Expected attribute '%s' to be of type %s, got %s"
                            .formatted(data.getName(), SimpleAttributeData.class.getSimpleName(), data.getClass().getSimpleName()));
                }
            }
            case CompositeAttribute compositeAttribute -> {
                if (data instanceof CompositeAttributeData compositeAttributeData) {
                    yield convert(compositeAttributeData, compositeAttribute);
                } else {
                    throw new InvalidDataException("Expected attribute '%s' to be of type %s, got %s"
                            .formatted(data.getName(), CompositeAttributeData.class.getSimpleName(), data.getClass().getSimpleName()));
                }
            }
        };
    }

    public Map<Field<Object>, Object> convert(SimpleAttributeData<?> data, SimpleAttribute attribute) {
        var field = (Field<Object>) JOOQUtils.resolveField(attribute);
        var value = data.getValue();
        return Map.of(field, value);
    }

    public Map<Field<Object>, Object> convert(CompositeAttributeData data, CompositeAttribute attribute) {
        var result = new HashMap<Field<Object>, Object>();
        for (var attributeData : data.getAttributes()) {
            var attr = attribute.getAttributeByName(attributeData.getName())
                    .orElseThrow(() -> new InvalidDataException("Attribute '%s' not found on attribute '%s'"
                            .formatted(attributeData.getName(), attribute.getName())));
            var converted = convert(attributeData, attr);
            result.putAll(converted);
        }
        return result;
    }
}
