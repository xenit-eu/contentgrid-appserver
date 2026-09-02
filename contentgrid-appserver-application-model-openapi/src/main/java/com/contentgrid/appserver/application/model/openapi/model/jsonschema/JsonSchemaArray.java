package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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

    @JsonInclude(Include.NON_NULL)
    private Boolean uniqueItems;

    public JsonSchemaArray(@NonNull OpenApiPotentialReference<JsonSchema> items) {
        super("array");
        this.items = items;
    }

    public JsonSchemaArray setUniqueItems(Boolean uniqueItems) {
        this.uniqueItems = uniqueItems;
        return this;
    }
}
