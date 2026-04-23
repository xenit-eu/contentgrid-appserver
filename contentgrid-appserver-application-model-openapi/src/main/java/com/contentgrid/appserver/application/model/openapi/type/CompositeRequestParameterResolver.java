package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.List;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompositeRequestParameterResolver implements RequestParameterResolver {
    @NonNull
    private final List<RequestParameterResolver> resolvers;

    @Override
    public Stream<OpenApiPotentialReference<OpenApiParameter>> resolveRequestParameters(HttpRequestType requestType, OpenApiSpecContext context) {
        return resolvers.stream()
                .flatMap(r -> r.resolveRequestParameters(requestType, context));
    }
}
