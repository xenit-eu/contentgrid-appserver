package com.contentgrid.appserver.rest.profile.assembler.json;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * An enum to represent JSON Schema pre-defined types.
 */
public enum JsonSchemaType {

    ARRAY, BOOLEAN, STRING, INTEGER, NUMBER, OBJECT;

    @JsonValue
    @Override
    public String toString() {
        return name().toLowerCase(Locale.US);
    }
}
