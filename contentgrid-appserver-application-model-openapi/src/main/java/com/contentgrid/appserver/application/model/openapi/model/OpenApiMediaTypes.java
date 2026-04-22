package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
public class OpenApiMediaTypes {
    @JsonValue
    Map<String, OpenApiBodyDescription> mediatypes = new LinkedHashMap<>();

    private static final String JSON = "application/json";

    public OpenApiBodyDescription getJson() {
        return getMediaType(MediaType.APPLICATION_JSON);
    }

    @JsonIgnore
    public OpenApiMediaTypes addJson(OpenApiPotentialReference<JsonSchema> jsonSchema) {
        return addMediaType(MediaType.APPLICATION_JSON, jsonSchema);
    }

    public OpenApiBodyDescription getMediaType(MediaType mediaType) {
        return mediatypes.get(mediaType.value);
    }

    public OpenApiMediaTypes addMediaType(String mediaType, OpenApiPotentialReference<JsonSchema> jsonSchema) {
        mediatypes.put(mediaType, new OpenApiBodyDescription().setSchema(jsonSchema));
        return this;
    }

    public OpenApiMediaTypes addMediaType(MediaType mediaType, OpenApiBodyDescription openApiBodyDescription) {
        mediatypes.put(mediaType.value, openApiBodyDescription);
        return this;
    }

    public OpenApiMediaTypes addMediaType(MediaType mediatype, @NonNull OpenApiPotentialReference<JsonSchema> jsonSchema) {
        return addMediaType(mediatype, new OpenApiBodyDescription().setSchema(jsonSchema));
    }

    @RequiredArgsConstructor
    public enum MediaType {
        APPLICATION_JSON("application/json"),
        APPLICATION_X_WWW_FORM_URLENCODED("application/x-www-form-urlencoded"),
        MULTIPART_FORM_DATA("multipart/form-data"),
        TEXT_URI_LIST("text/uri-list");
        @JsonValue
        public final String value;

    }
}
