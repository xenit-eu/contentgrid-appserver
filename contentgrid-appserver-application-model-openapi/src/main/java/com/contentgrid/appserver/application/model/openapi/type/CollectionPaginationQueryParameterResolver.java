package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaInteger;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import java.util.List;
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
                new OpenApiParameter("_cursor", In.QUERY)
                        .setDescription("Cursor to access a page (cursors are server-generated and supplied in the page metadata)")
                        .setSchema(new JsonSchemaString().setExamples(List.of("1mlpulv1"))),
                new OpenApiParameter("_size", In.QUERY)
                        .setDescription("Page size")
                        .setSchema(new JsonSchemaInteger())
        );
    }
}
