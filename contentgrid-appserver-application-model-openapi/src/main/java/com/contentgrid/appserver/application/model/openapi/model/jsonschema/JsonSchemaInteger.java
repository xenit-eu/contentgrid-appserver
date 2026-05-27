package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class JsonSchemaInteger extends JsonSchemaNumber {

    public JsonSchemaInteger() {
        super("integer");
    }
}
