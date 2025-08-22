package com.contentgrid.appserver.rest.assembler.profile.json;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

@Value(staticConstructor = "of")
public class JsonSchemaReference {

    String name;

    @JsonValue
    @Override
    public String toString() {
        return String.format("#/definitions/%s", name);
    }
}
