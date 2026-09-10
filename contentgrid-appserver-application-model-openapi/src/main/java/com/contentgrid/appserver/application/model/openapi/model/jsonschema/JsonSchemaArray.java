package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;


@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = false)
public final class JsonSchemaArray extends AbstractJsonSchemaDataType {

    @NonNull
    private final OpenApiPotentialReference<JsonSchema> items;

    @Setter
    @Accessors(chain = true)
    @JsonInclude(Include.NON_DEFAULT)
    private boolean uniqueItems;

    public JsonSchemaArray(@NonNull OpenApiPotentialReference<JsonSchema> items) {
        super("array");
        this.items = items;
    }
}
