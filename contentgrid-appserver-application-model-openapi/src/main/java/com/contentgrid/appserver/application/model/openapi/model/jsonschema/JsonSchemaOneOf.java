package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Accessors(chain = true)
public final class JsonSchemaOneOf implements JsonSchema {
    @NonNull
    final List<? extends OpenApiPotentialReference<JsonSchema>> oneOf;

    @SafeVarargs
    public JsonSchemaOneOf(OpenApiPotentialReference<JsonSchema>... oneOf) {
        this(List.of(oneOf));
    }
}
