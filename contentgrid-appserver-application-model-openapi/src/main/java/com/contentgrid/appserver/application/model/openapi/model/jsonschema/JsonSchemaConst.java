package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

@Value
public class JsonSchemaConst implements JsonSchema {
    @JsonProperty("const")
    @Getter(AccessLevel.NONE)
    Object const_;

    public Object getConst() {
        return const_;
    }

}
