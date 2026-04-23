package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.stream.Stream;

public interface RequestParameterResolver {
    Stream<OpenApiPotentialReference<OpenApiParameter>> resolveRequestParameters(HttpRequestType requestType, OpenApiSpecContext context);
}
