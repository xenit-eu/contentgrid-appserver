package com.contentgrid.appserver.application.model.openapi.resolver;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiHttpHeaders.OpenApiHeaderDescription;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import com.contentgrid.appserver.application.model.openapi.type.AttributeType;
import com.contentgrid.appserver.application.model.openapi.type.HttpRequestType;
import com.contentgrid.appserver.application.model.openapi.type.HttpResponseType;
import com.contentgrid.appserver.application.model.openapi.type.SemanticType;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

public class ContentDispositionHeadersResolver implements ResponseHeaderResolver, RequestParameterResolver {

    private static final String CONTENT_DISPOSITION = "Content-Disposition";

    private boolean hasContentDisposition(SemanticType type) {
        return type instanceof AttributeType.ContentAttributeType;
    }

    @Override
    public Stream<Entry<String, OpenApiPotentialReference<OpenApiHeaderDescription>>> resolveResponseHeaders(
            HttpResponseType responseType, OpenApiSpecContext context) {
        if(!hasContentDisposition(responseType.getType())) {
            return Stream.empty();
        }
        if(!responseType.getStatusCode().is(200)) {
            return Stream.empty();
        }

        return Stream.of(Map.entry(
                CONTENT_DISPOSITION, context.spec().getComponents().getHeaders().register(CONTENT_DISPOSITION, () -> {
                    var h = new OpenApiHeaderDescription();
                    h.setDescription("Content-Disposition is always set to attachment, with optionally a filename");
                    h.setRequired(true);
                    h.setSchema(createContentDispositionSchema(context));
                    return h;
                })
        ));
    }

    @Override
    public Stream<OpenApiPotentialReference<OpenApiParameter>> resolveRequestParameters(HttpRequestType requestType,
            OpenApiSpecContext context) {
        if (!hasContentDisposition(requestType.getType())) {
            return Stream.empty();
        }
        if(requestType.getMethod() != HttpMethod.POST && requestType.getMethod() != HttpMethod.PUT) {
            return Stream.empty();
        }
        return Stream.of(
                context.spec().getComponents().getParameters().register("header.Content-Disposition",
                        () -> new OpenApiParameter(CONTENT_DISPOSITION, In.HEADER)
                                .setDescription(
                                        "The disposition type is ignored, only the optional filename parameter is used to optionally set a filename")
                                .setSchema(createContentDispositionSchema(context))
                )
        );
    }

    private OpenApiPotentialReference<JsonSchema> createContentDispositionSchema(OpenApiSpecContext context) {
        return context.spec().getComponents().getSchemas().register("RFC6266.Content-Disposition", () -> new JsonSchemaString()
                .setDescription("See [RFC 6266](https://datatracker.ietf.org/doc/html/rfc6266)")
                .setExamples(List.of(
                        "attachment",
                        "attachment; filename=\"my-file.pdf\"",
                        "attachment; filename=\"=?UTF-8?Q?my-file.pdf?=\"; filename*=UTF-8''my-file.pdf"
                ))
        );
    }
}
