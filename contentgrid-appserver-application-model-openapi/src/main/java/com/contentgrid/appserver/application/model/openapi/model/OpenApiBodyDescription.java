package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaOneOf;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class OpenApiBodyDescription {
    OpenApiPotentialReference<JsonSchema> schema;
    @JsonInclude(Include.NON_NULL)
    Object example;

    public OpenApiBodyDescription combinedWith(OpenApiBodyDescription description) {
        if(schema != null && description.schema != null) {
            schema = new JsonSchemaOneOf(schema, description.schema);
        } else if(description.schema != null) {
            schema = description.schema;
        }

        if (description.example != null) {
            example = description.example;
        }

        return this;
    }
}
