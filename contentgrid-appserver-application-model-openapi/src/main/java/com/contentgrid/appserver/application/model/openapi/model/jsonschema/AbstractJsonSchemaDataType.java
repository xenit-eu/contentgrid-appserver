package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@RequiredArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Accessors(chain = true)
public abstract sealed class AbstractJsonSchemaDataType implements JsonSchema permits JsonSchemaArray, JsonSchemaBoolean,
        JsonSchemaNull, JsonSchemaNumber, JsonSchemaObject, JsonSchemaString {
    @NonNull
    String type;

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
}
