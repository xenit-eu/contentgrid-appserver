package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiDiscriminator;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
final public class JsonSchemaOneOf implements JsonSchema {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    OpenApiDiscriminator discriminator;

    @NonNull
    final List<? extends OpenApiPotentialReference<JsonSchema>> oneOf;

    public JsonSchemaOneOf(OpenApiPotentialReference<JsonSchema>... oneOf) {
        this(List.of(oneOf));
    }
}
