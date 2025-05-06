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
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import org.jooq.Field;

@UtilityClass
public class EntityDataConverter {

    public List<JOOQPair<Object>> convert(EntityData data, Entity entity) {
        var result = new ArrayList<JOOQPair<Object>>();
        for (var attributeData : data.getAttributes()) {
            var attribute = entity.getAttributeByName(attributeData.getName())
                    .orElseThrow(() -> new InvalidDataException("Attribute '%s' not found on entity '%s'"
                            .formatted(attributeData.getName(), entity.getName())));
            var converted = convert(attributeData, attribute);
            result.addAll(converted);
        }
        return result;
    }

    public List<JOOQPair<Object>> convert(AttributeData data, Attribute attribute) {
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

    public List<JOOQPair<Object>> convert(SimpleAttributeData<?> data, SimpleAttribute attribute) {
        checkType(attribute.getType(), data.getValue());
        var field = (Field<Object>) JOOQUtils.resolveField(attribute);
        return List.of(new JOOQPair<>(field, data.getValue()));
    }

    public List<JOOQPair<Object>> convert(CompositeAttributeData data, CompositeAttribute attribute) {
        var result = new ArrayList<JOOQPair<Object>>();
        for (var attributeData : data.getAttributes()) {
            var attr = attribute.getAttributeByName(attributeData.getName())
                    .orElseThrow(() -> new InvalidDataException("Attribute '%s' not found on attribute '%s'"
                            .formatted(attributeData.getName(), attribute.getName())));
            var converted = convert(attributeData, attr);
            result.addAll(converted);
        }
        return result;
    }

    private void checkType(SimpleAttribute.Type type, Object value) {
        if (value == null) {
            return;
        }
        switch (type) {
            case TEXT -> {
                if (!(value instanceof String)) {
                    throw new InvalidDataException("Expected value to be of type %s, got %s"
                            .formatted(String.class.getSimpleName(), value.getClass().getSimpleName()));
                }
            }
            case LONG -> {
                var longTypes = Stream.of(int.class, long.class, Integer.class, Long.class);
                if (longTypes.noneMatch(clazz -> clazz.isInstance(value))) {
                    throw new InvalidDataException("Expected value to be an integer type, got %s".formatted(value.getClass().getSimpleName()));
                }
            }
            case DOUBLE -> {
                var doubleTypes = Stream.of(float.class, double.class, Float.class, Double.class, BigDecimal.class);
                if (doubleTypes.noneMatch(clazz -> clazz.isInstance(value))) {
                    throw new InvalidDataException("Expected value to be decimal, got %s".formatted(value.getClass().getSimpleName()));
                }
            }
            case BOOLEAN -> {
                var boolTypes = Stream.of(boolean.class, Boolean.class);
                if (boolTypes.noneMatch(clazz -> clazz.isInstance(value))) {
                    throw new InvalidDataException("Expected value to be a boolean, got %s".formatted(value.getClass().getSimpleName()));
                }
            }
            case DATETIME -> {
                if (!(value instanceof Temporal)) {
                    throw new InvalidDataException("Expected value to be of type %s, got %s"
                            .formatted(Temporal.class.getSimpleName(), value.getClass().getSimpleName()));
                }
            }
            case UUID -> {
                if (!(value instanceof UUID)) {
                    throw new InvalidDataException("Expected value to be of type %s, got %s"
                            .formatted(UUID.class.getSimpleName(), value.getClass().getSimpleName()));
                }
            }
        }
    }

    public record JOOQPair<T>(@NonNull Field<T> field, T value) {}
}
