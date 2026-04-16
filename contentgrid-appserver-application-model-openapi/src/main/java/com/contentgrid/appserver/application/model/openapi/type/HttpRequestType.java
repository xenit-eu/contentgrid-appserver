package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import lombok.NonNull;
import lombok.Value;

@Value
public class HttpRequestType {
    @NonNull
    HttpMethod method;
    @NonNull
    SemanticType type;
}
