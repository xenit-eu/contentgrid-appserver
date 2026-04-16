package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;


@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = false)
public final class JsonSchemaArray extends AbstractJsonSchemaDataType {

    @NonNull
    private final OpenApiPotentialReference<JsonSchema> items;

    public JsonSchemaArray(@NonNull OpenApiPotentialReference<JsonSchema> items) {
        super("array");
        this.items = items;
    }
}
