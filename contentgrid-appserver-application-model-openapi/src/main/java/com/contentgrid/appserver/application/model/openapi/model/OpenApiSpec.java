package com.contentgrid.appserver.application.model.openapi.model;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Value
public class OpenApiSpec {
    @NonNull
    String openapi;
    @NonNull
    OpenApiInfo info;
    OpenApiPaths paths = new OpenApiPaths();
    OpenApiComponents components = new OpenApiComponents();
    List<OpenApiTag> tags = new ArrayList<>();
}
