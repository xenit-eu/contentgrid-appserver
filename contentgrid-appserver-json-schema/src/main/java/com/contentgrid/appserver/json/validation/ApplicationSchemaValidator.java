package com.contentgrid.appserver.json.validation;

import com.contentgrid.appserver.json.exceptions.SchemaValidationException;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion.VersionFlag;
import java.util.stream.Collectors;

public class ApplicationSchemaValidator {

    /* package-private for testing */
    static final JsonSchema schema = JsonSchemaFactory.getInstance(VersionFlag.V7, builder -> builder.schemaMappers(
                    schemaMappers -> schemaMappers.mapPrefix("https://contentgrid.cloud/schemas/", "classpath:/schemas/")))
            .getSchema(SchemaLocation.of("https://contentgrid.cloud/schemas/application-schema.json"));


    public void validate(String json) throws SchemaValidationException {
        var validationResult = schema.validate(json, InputFormat.JSON);
        if (!validationResult.isEmpty()) {
            String errorMessage = validationResult.stream()
                    .map(error -> String.format("Error at %s: %s", error.getInstanceLocation(), error.getMessage()))
                    .collect(Collectors.joining(", "));
            throw new SchemaValidationException("Invalid JSON schema: " + errorMessage);
        }
    }

}
