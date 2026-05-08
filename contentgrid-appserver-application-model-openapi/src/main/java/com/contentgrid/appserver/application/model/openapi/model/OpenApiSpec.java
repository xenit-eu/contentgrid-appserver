package com.contentgrid.appserver.application.model.openapi.model;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@RequiredArgsConstructor
public class OpenApiSpec {
    @NonNull
    String openapi;
    @NonNull
    OpenApiInfo info;
    Set<OpenApiTag> tags = new TreeSet<>(Comparator.comparing(OpenApiTag::getName));
    OpenApiPaths paths = new OpenApiPaths();
    OpenApiComponents components = new OpenApiComponents();
}
