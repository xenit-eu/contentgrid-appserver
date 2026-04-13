package com.contentgrid.appserver.application.model.openapi.model;

import lombok.NonNull;
import lombok.Value;

@Value
public class OpenApiInfo {
    @NonNull
    String title;
    String summary;
    String description;
    @NonNull
    String version;
}
