package com.contentgrid.appserver.application.model.openapi.model.jsonschema;


import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
public final class JsonSchemaBoolean extends AbstractJsonSchemaDataType {

    public JsonSchemaBoolean() {
        super("boolean");
    }
}
