package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import lombok.NonNull;
import lombok.Value;

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
