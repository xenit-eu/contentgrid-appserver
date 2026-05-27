package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.NonNull;
import lombok.Value;

/**
 * The object provides metadata about the API.
 * @see <a href="https://spec.openapis.org/oas/v3.2.0.html#info-object">Info Object</a>
 */
@Value
public class OpenApiInfo {
    @NonNull
    String title;
    @JsonInclude(Include.NON_NULL)
    String summary;
    @JsonInclude(Include.NON_NULL)
    String description;
    @NonNull
    String version;
}
