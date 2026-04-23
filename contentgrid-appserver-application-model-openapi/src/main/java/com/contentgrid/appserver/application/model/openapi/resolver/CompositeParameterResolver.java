package com.contentgrid.appserver.application.model.openapi.resolver;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiHttpHeaders.OpenApiHeaderDescription;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.type.HttpRequestType;
import com.contentgrid.appserver.application.model.openapi.type.HttpResponseType;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompositeParameterResolver implements RequestParameterResolver, ResponseHeaderResolver {
    private final List<Object> resolvers;

    private <T, R> Stream<T> resolve(Class<R> resolverType, Function<R, Stream<T>> doResolve) {
        return resolvers.stream()
                .filter(resolverType::isInstance)
                .flatMap(resolver -> doResolve.apply((R)resolver));
    }

    @Override
    public Stream<OpenApiPotentialReference<OpenApiParameter>> resolveRequestParameters(HttpRequestType requestType,
            OpenApiSpecContext context) {
        return resolve(RequestParameterResolver.class, r -> r.resolveRequestParameters(requestType, context));
    }

    @Override
    public Stream<Entry<String, OpenApiPotentialReference<OpenApiHeaderDescription>>> resolveResponseHeaders(
            HttpResponseType responseType, OpenApiSpecContext context) {
        return resolve(ResponseHeaderResolver.class, r -> r.resolveResponseHeaders(responseType, context));
    }
}
