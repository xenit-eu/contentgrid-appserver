package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import lombok.NonNull;

public sealed class JsonSchemaNumber extends AbstractJsonSchemaDataType permits JsonSchemaInteger {

    JsonSchemaNumber(@NonNull String type) {
        super(type);
    }

    public JsonSchemaNumber() {
        super("number");
    }
}
