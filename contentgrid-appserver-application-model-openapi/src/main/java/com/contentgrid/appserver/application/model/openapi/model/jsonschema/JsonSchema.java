package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;

public sealed interface JsonSchema extends OpenApiPotentialReference<JsonSchema> permits AbstractJsonSchemaDataType, JsonSchemaOneOf {

}
