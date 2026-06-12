package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

/**
 * The root object of the OpenApi Description
 * <p>
 * @see <a href="https://spec.openapis.org/oas/v3.2.0.html#openapi-object">OpenAPI Object</a>
 */
@Value
@RequiredArgsConstructor
@JsonPropertyOrder({"openapi", "info", "tags", "paths", "components"})
public class OpenApiSpec {
    @NonNull
    String openapi;
    @NonNull
    OpenApiInfo info;
    Set<OpenApiTag> tags = new TreeSet<>(Comparator.comparing(OpenApiTag::getName));
    OpenApiPaths paths = new OpenApiPaths();
    OpenApiComponents components = new OpenApiComponents();
}
