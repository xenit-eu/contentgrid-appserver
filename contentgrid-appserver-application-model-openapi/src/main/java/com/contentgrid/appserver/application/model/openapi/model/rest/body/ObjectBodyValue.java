package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import java.util.Map;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * A {@link BodyValue} representing a key-value object — used both as the top-level body returned
 * by {@link BodyObjectMapper} and as the value of a nested object field.
 * <p>
 * Field names are the map keys; use a {@link java.util.LinkedHashMap} when constructing to
 * preserve attribute ordering.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder(toBuilder = true)
@RequiredArgsConstructor
public class ObjectBodyValue extends BodyValue {

    @NonNull
    @Singular
    Map<String, BodyValue> fields;

    public Optional<BodyValue> getField(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public ObjectBodyValue withField(String name, BodyValue value) {
        return toBuilder().field(name, value).build();
    }
}
