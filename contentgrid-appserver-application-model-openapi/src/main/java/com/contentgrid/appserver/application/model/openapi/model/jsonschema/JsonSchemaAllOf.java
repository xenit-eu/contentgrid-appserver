package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class JsonSchemaAllOf implements JsonSchema {

    @NonNull
    List<OpenApiPotentialReference<JsonSchema>> allOf;

    @SafeVarargs
    public JsonSchemaAllOf(OpenApiPotentialReference<JsonSchema>... allOf) {
        this(List.of(allOf));
    }
}
