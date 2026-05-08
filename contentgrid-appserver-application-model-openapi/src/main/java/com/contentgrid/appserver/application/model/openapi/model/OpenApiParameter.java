package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Comparator;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OpenApiParameter implements OpenApiPotentialReference<OpenApiParameter>, Comparable<OpenApiParameter> {
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

    private static final Comparator<OpenApiParameter> COMPARATOR = Comparator.comparing(OpenApiParameter::getIn)
            .thenComparing(OpenApiParameter::getName, Comparators.UNDERSCORE_LAST);

    @Override
    public int compareTo(OpenApiParameter openApiParameter) {
        return COMPARATOR.compare(this, openApiParameter);
    }

    public enum In {
        @JsonProperty("path")
        PATH,
        @JsonProperty("query")
        QUERY,
        @JsonProperty("header")
        HEADER
    }


}
