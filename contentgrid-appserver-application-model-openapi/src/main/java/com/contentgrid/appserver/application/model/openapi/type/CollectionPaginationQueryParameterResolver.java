package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import java.util.stream.Stream;

public class CollectionPaginationQueryParameterResolver implements RequestParameterResolver {

    private boolean supports(HttpRequestType requestType) {
        return requestType.getType() instanceof CollectionType collectionType && collectionType.getElementType() instanceof EntityType;
    }

    @Override
    public Stream<OpenApiParameter> resolveRequestParameters(HttpRequestType requestType, OpenApiSpecContext context) {
        if (!supports(requestType)) {
            return Stream.empty();
        }

        return Stream.of(
                new OpenApiParameter("_cursor", In.QUERY),
                new OpenApiParameter("_size", In.QUERY)
        );
    }
}
