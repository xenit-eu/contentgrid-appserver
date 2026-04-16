package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public sealed class JsonSchemaNumber extends AbstractJsonSchemaDataType permits JsonSchemaInteger {

    JsonSchemaNumber(@NonNull String type) {
        super(type);
    }

    public JsonSchemaNumber() {
        super("number");
    }
}
