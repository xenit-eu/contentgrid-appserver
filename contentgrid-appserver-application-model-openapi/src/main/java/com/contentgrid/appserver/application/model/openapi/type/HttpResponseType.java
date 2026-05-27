package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation.HttpStatusCode;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import lombok.NonNull;
import lombok.Value;

@Value
public class HttpResponseType {
    @NonNull
    HttpMethod method;
    @NonNull
    HttpStatusCode statusCode;
    @NonNull
    SemanticType type;
}
