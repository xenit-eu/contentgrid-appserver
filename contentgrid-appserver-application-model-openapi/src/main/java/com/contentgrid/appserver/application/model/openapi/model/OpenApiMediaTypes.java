package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.Value;

@Value
public class OpenApiMediaTypes {
    @JsonAnyGetter
    Map<String, OpenApiBodyDescription> mediatypes = new LinkedHashMap<>();

    public OpenApiMediaTypes addMediaType(String mediatype, @NonNull OpenApiBodyDescription bodyDescription) {
        mediatypes.put(mediatype, bodyDescription);
        return this;
    }

    public OpenApiMediaTypes addMediaType(String mediatype, @NonNull OpenApiPotentialReference<JsonSchema> jsonSchema) {
        return addMediaType(mediatype, new OpenApiBodyDescription().setSchema(jsonSchema));
    }
}
