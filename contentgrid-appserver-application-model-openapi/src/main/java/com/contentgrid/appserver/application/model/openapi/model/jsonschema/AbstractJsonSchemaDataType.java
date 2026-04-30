package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Accessors(chain = true)
public abstract sealed class AbstractJsonSchemaDataType implements JsonSchema permits JsonSchemaArray, JsonSchemaBoolean,
        JsonSchemaNull, JsonSchemaNumber, JsonSchemaObject, JsonSchemaString {
    @NonNull
    DataType type;

    @JsonInclude(Include.NON_EMPTY)
    String title;

    @JsonInclude(Include.NON_EMPTY)
    String description;

    @JsonInclude(Include.NON_DEFAULT)
    boolean deprecated;

    @JsonInclude(Include.NON_EMPTY)
    List<Object> examples;

    @JsonInclude(Include.NON_DEFAULT)
    boolean readOnly;

    @JsonInclude(Include.NON_DEFAULT)
    boolean writeOnly;

    public AbstractJsonSchemaDataType(String type) {
        this(DataType.of(type));
    }

    public AbstractJsonSchemaDataType orNull() {
        type = type.withType("null");
        return this;
    }

    @Value
    public static class DataType {
        @NonNull
        @With(AccessLevel.PRIVATE)
        Set<String> types;

        public static DataType of(@NonNull String type) {
            return new DataType(Set.of(type));
        }

        public DataType withType(@NonNull String type) {
            var copy = new LinkedHashSet<>(types);
            copy.add(type);
            return withTypes(Collections.unmodifiableSet(copy));
        }

        @JsonValue
        public Object jsonValue() {
            return switch (types.size()) {
                case 0 -> null;
                case 1 -> types.iterator().next();
                default -> types;
            };
        }
    }
}
