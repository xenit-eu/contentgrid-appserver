package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import lombok.Getter;
import lombok.NonNull;


@Getter
public final class JsonSchemaArray extends AbstractJsonSchemaDataType {

    @NonNull
    private final OpenApiPotentialReference<JsonSchema> items;

    public JsonSchemaArray(@NonNull OpenApiPotentialReference<JsonSchema> items) {
        super("array");
        this.items = items;
    }
}
