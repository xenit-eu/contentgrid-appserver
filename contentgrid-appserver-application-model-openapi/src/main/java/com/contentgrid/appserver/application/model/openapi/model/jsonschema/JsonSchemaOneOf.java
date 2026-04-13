package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.List;
import lombok.NonNull;
import lombok.Value;

@Value
public class JsonSchemaOneOf implements JsonSchema {
    @NonNull
    List<OpenApiPotentialReference<JsonSchema>> oneOf;

}
