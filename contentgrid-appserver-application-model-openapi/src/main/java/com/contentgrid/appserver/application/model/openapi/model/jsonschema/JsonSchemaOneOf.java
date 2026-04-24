package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class JsonSchemaOneOf implements JsonSchema {
    @NonNull
    List<? extends OpenApiPotentialReference<JsonSchema>> oneOf;

    public JsonSchemaOneOf(OpenApiPotentialReference<JsonSchema>... oneOf) {
        this(List.of(oneOf));
    }
}
