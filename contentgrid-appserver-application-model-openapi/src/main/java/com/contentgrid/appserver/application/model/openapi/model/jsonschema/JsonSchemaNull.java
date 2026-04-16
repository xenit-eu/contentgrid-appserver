package com.contentgrid.appserver.application.model.openapi.model.jsonschema;


import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public final class JsonSchemaNull extends AbstractJsonSchemaDataType {

    public JsonSchemaNull() {
        super("null");
    }
}
