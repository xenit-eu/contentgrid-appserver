package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.Optional;

public sealed interface JsonSchema extends OpenApiPotentialReference<JsonSchema> permits AbstractJsonSchemaDataType,
        JsonSchemaAllOf, JsonSchemaComposite, JsonSchemaConst, JsonSchemaDiscriminator, JsonSchemaOneOf {

    default <T extends JsonSchema> Optional<T> ofType(Class<T> type) {
        if (type.isInstance(this)) {
            return Optional.of((T) this);
        }
        return Optional.empty();
    }

}
