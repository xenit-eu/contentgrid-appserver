package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OpenApiParameter {
    @NonNull
    String name;
    @NonNull
    In in;

    @JsonInclude(Include.NON_NULL)
    String description;

    @JsonInclude(Include.NON_DEFAULT)
    boolean required;

    @JsonInclude(Include.NON_DEFAULT)
    boolean deprecated;

    @JsonInclude(Include.NON_NULL)
    Object example;

    @JsonInclude(Include.NON_NULL)
    OpenApiPotentialReference<JsonSchema> schema;

    public enum In {
        @JsonProperty("query")
        QUERY,
        @JsonProperty("header")
        HEADER,
        @JsonProperty("path")
        PATH
    }

}
