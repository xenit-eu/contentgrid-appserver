package com.contentgrid.appserver.application.model.openapi.model.jsonschema;


import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public final class JsonSchemaNull extends AbstractJsonSchemaDataType {

    public JsonSchemaNull() {
        super("null");
    }
}
