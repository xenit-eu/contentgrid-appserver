package com.contentgrid.appserver.application.model.openapi.resolver;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation.HttpStatusCode;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiResponse;
import com.contentgrid.appserver.application.model.openapi.type.HttpRequestType;
import java.util.Map.Entry;
import java.util.stream.Stream;

public interface ResponseResolver {
    Stream<Entry<HttpStatusCode, OpenApiPotentialReference<OpenApiResponse>>> resolveResponse(
            HttpRequestType requestType,
            OpenApiSpecContext context
    );

}
