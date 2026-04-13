package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class OpenApiReference<T extends OpenApiPotentialReference<T>> implements OpenApiPotentialReference<T> {
    @JsonProperty("$ref")
    String reference;
}
