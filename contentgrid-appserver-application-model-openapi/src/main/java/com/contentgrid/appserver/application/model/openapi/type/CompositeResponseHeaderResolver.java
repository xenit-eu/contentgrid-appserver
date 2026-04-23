package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiHttpHeaders.OpenApiHeaderDescription;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompositeResponseHeaderResolver implements ResponseHeaderResolver {
    @NonNull
    private final List<ResponseHeaderResolver> resolvers;

    @Override
    public Stream<Entry<String, OpenApiPotentialReference<OpenApiHeaderDescription>>> resolveResponseHeaders(HttpResponseType responseType,
            OpenApiSpecContext context) {
        return resolvers.stream()
                .flatMap(resolver -> resolver.resolveResponseHeaders(responseType, context));
    }
}
