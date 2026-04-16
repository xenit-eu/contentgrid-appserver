package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiHttpHeaders.OpenApiHeaderDescription;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * Adds the versioning headers to supported contexts.
 * <p>
 * Headers:
 * <ul>
 *     <li>If-Match/If-None-Match request parameters for all methods except POST (version is not known before the item is created)
 *     <li>ETag response parameter for all methods except DELETE (version is no longer relevant for item that is now gone)
 * </ul>
 * <p>
 * The supported contexts are:
 * <ul>
 *     <li>Entities that have an {@link ETagFlag} field (where the ETag is stored)
 *     <li>All content attributes (ETag always available)
 * </ul>
 *
 */
public class VersioningHeadersResolver implements RequestParameterResolver, ResponseHeaderResolver {

    private boolean hasETag(SemanticType targetType, OpenApiSpecContext context) {
        return switch (targetType) {
            case EntityType entityType -> context.application().getRequiredEntityByName(entityType.getEntityName()).getAttributes()
                    .stream()
                    .anyMatch(a -> a.hasFlag(ETagFlag.class));
            case AttributeType.ContentAttributeType contentAttributeType -> true;
            case null, default -> false;
        };
    }

    @Override
    public Stream<OpenApiParameter> resolveRequestParameters(HttpRequestType requestType, OpenApiSpecContext context) {
        if (!hasETag(requestType.getType(), context) || requestType.getMethod() == HttpMethod.POST) {
            return Stream.empty();
        }

        return Stream.of(
                new OpenApiParameter("If-Match", In.HEADER),
                new OpenApiParameter("If-None-Match", In.HEADER)
        );

    }

    @Override
    public Stream<Entry<String, OpenApiHeaderDescription>> resolveResponseHeaders(HttpResponseType responseType,
            OpenApiSpecContext context) {
        if(!hasETag(responseType.getType(), context) || responseType.getMethod() == HttpMethod.DELETE || responseType.getStatusCode().isError()) {
            return Stream.empty();
        }
        return Stream.of(
                Map.entry("ETag", new OpenApiHeaderDescription().setRequired(true))
        );
    }
}
