package com.contentgrid.appserver.application.model.openapi.model.jsonschema;


import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class JsonSchemaBoolean extends AbstractJsonSchemaDataType {

    public JsonSchemaBoolean() {
        super("boolean");
    }
}
