package com.contentgrid.appserver.query.engine.jooq;

import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.query.engine.api.data.AttributeData;
import com.contentgrid.appserver.query.engine.api.data.CompositeAttributeData;
import com.contentgrid.appserver.query.engine.api.data.EntityData;
import com.contentgrid.appserver.query.engine.api.data.EntityId;
import com.contentgrid.appserver.query.engine.api.data.SimpleAttributeData;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.Map;
import java.util.UUID;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EntityDataMapper {

    public EntityData from(@NonNull Entity entity, Map<String, Object> data) {
        var builder = EntityData.builder()
                .name(entity.getName())
                .id(getEntityId(entity, data));
        for (var attribute : entity.getAttributes()) {
            builder.attribute(from(attribute, data));
        }
        return builder.build();
    }

    private EntityId getEntityId(@NonNull Entity entity, Map<String, Object> data) {
        var primaryKey = entity.getPrimaryKey();
        var id = convert(primaryKey, data.get(primaryKey.getColumn().getValue()));
        if (id instanceof UUID uuid) {
            return EntityId.of(uuid);
        } else {
            throw new IllegalStateException("Primary key column '%s' with value '%s' not supported".formatted(primaryKey.getColumn(), id));
        }
    }

    public AttributeData from(@NonNull Attribute attribute, Map<String, Object> data) {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> from(simpleAttribute, data);
            case CompositeAttribute compositeAttribute -> from(compositeAttribute, data);
        };
    }

    public SimpleAttributeData<?> from(@NonNull SimpleAttribute attribute, Map<String, Object> data) {
        var value = convert(attribute, data.get(attribute.getColumn().getValue()));
        return SimpleAttributeData.builder()
                .name(attribute.getName())
                .value(value)
                .build();
    }

    public CompositeAttributeData from(@NonNull CompositeAttribute attribute, Map<String, Object> data) {
        var builder = CompositeAttributeData.builder().name(attribute.getName());
        for (var child : attribute.getAttributes()) {
            builder.attribute(from(child, data));
        }
        return builder.build();
    }

    private Object convert(SimpleAttribute attribute, Object value) {
        if (value == null) {
            return null;
        }
        return switch (attribute.getType()) {
            case TEXT -> {
                if (value instanceof String string) {
                    yield string;
                }
                throw new IllegalStateException("Value of attribute '%s' is not a string".formatted(attribute.getName()));
            }
            case LONG -> {
                if (value instanceof Number number) {
                    yield number.longValue();
                }
                throw new IllegalStateException("Value of attribute '%s' is not numeric".formatted(attribute.getName()));
            }
            case DOUBLE -> switch (value) {
                case BigDecimal number -> number;
                case Double number -> BigDecimal.valueOf(number);
                case Float number -> BigDecimal.valueOf(number);
                default -> throw new IllegalStateException("Value of attribute '%s' is not decimal".formatted(attribute.getName()));
            };
            case BOOLEAN -> {
                if (value instanceof Boolean bool) {
                    yield bool;
                }
                throw new IllegalStateException("Value of attribute '%s' is not a boolean".formatted(attribute.getName()));
            }
            case DATETIME -> {
                if (value instanceof Temporal temporal) {
                    yield Instant.from(temporal);
                }
                throw new IllegalStateException("Value of attribute '%s' is not a datetime".formatted(attribute.getName()));
            }
            case UUID -> {
                if (value instanceof UUID uuid) {
                    yield uuid;
                }
                throw new IllegalStateException("Value of attribute '%s' is not a uuid".formatted(attribute.getName()));
            }
        };
    }

}
