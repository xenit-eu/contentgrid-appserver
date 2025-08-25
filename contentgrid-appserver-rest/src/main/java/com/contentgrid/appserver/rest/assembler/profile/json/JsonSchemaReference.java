package com.contentgrid.appserver.rest.assembler.profile.json;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

/**
 * A value object to represent a JSON Schema reference name.
 */
@Value(staticConstructor = "named")
public class JsonSchemaReference {

    String name;

    @JsonValue
    @Override
    public String toString() {
        return "#/$defs/" + name;
    }
}
