package com.contentgrid.appserver.application.model.openapi.resolver;

import static com.contentgrid.appserver.application.model.openapi.ProblemDetail.ProblemDetailCustomizer.requiredProperty;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetail.ProblemDetailCustomizer.status;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetail.ProblemDetailCustomizer.type;

import com.contentgrid.appserver.application.model.attributes.flags.ETagFlag;
import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.ProblemDetail;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiHttpHeaders.OpenApiHeaderDescription;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation.HttpStatusCode;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiResponse;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaArray;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaConst;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaOneOf;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import com.contentgrid.appserver.application.model.openapi.type.AttributeType;
import com.contentgrid.appserver.application.model.openapi.type.EntityType;
import com.contentgrid.appserver.application.model.openapi.type.HttpRequestType;
import com.contentgrid.appserver.application.model.openapi.type.HttpResponseType;
import com.contentgrid.appserver.application.model.openapi.type.RelationItemType;
import com.contentgrid.appserver.application.model.openapi.type.RelationType;
import com.contentgrid.appserver.application.model.openapi.type.SemanticType;
import java.util.List;
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
public class VersioningHeadersResolver implements RequestParameterResolver, ResponseHeaderResolver, ResponseResolver {

    private boolean hasETag(SemanticType targetType, OpenApiSpecContext context) {
        return switch (targetType) {
            case RelationItemType relationItemType -> false;
            case EntityType entityType -> context.application().getRequiredEntityByName(entityType.getEntityName()).getAttributes()
                    .stream()
                    .anyMatch(a -> a.hasFlag(ETagFlag.class));
            case AttributeType.ContentAttributeType contentAttributeType -> true;
            case RelationType relationType -> relationType.getTarget() instanceof EntityType;
            case null, default -> false;
        };
    }

    private boolean hasMatchHeaders(HttpRequestType requestType, OpenApiSpecContext context) {
        return hasETag(requestType.getType(), context) && requestType.getMethod() != HttpMethod.POST;
    }

    @Override
    public Stream<OpenApiPotentialReference<OpenApiParameter>> resolveRequestParameters(HttpRequestType requestType, OpenApiSpecContext context) {
        if (!hasMatchHeaders(requestType, context)) {
            return Stream.empty();
        }

        var matchSchema = new JsonSchemaOneOf(
                new JsonSchemaConst("*"),
                new JsonSchemaArray(createEntityTagSchema(context))
        );

        return Stream.of(
                context.spec().getComponents().getParameters().register("header.If-Match", () -> new OpenApiParameter("If-Match", In.HEADER)
                        .setDescription("See [RFC9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-if-match)")
                        .setSchema(matchSchema)
                ),
                context.spec().getComponents().getParameters().register("header.If-None-Match", () -> new OpenApiParameter("If-None-Match", In.HEADER)
                        .setDescription("See [RFC9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-if-none-match)")
                        .setSchema(matchSchema)
                )
        );

    }

    @Override
    public Stream<Entry<String, OpenApiPotentialReference<OpenApiHeaderDescription>>> resolveResponseHeaders(
            HttpResponseType responseType,
            OpenApiSpecContext context) {
        if(!hasETag(responseType.getType(), context) || responseType.getMethod() == HttpMethod.DELETE || responseType.getStatusCode().isError()) {
            return Stream.empty();
        }
        return Stream.of(
                Map.entry("ETag", context.spec().getComponents().getHeaders().register("ETag", () -> new OpenApiHeaderDescription().setRequired(true)
                                .setDescription("See [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-etag)")
                                .setSchema(createEntityTagSchema(context))
                        )
                )
        );
    }

    private OpenApiPotentialReference<JsonSchema> createEntityTagSchema(OpenApiSpecContext context) {
        return context.spec().getComponents().getSchemas().register("RFC9110.entity-tag", () -> new JsonSchemaString()
                .setPattern("^\"[a-z0-9]+\"$")
                .setDescription("A strong ETag value")
                .setExamples(List.of(
                        "\"1mktvx6\"",
                        "\"25borvrzr9thvwuua39m3c2a3\""
                )));
    }

    @Override
    public Stream<Entry<HttpStatusCode, OpenApiPotentialReference<OpenApiResponse>>> resolveResponse(
            HttpRequestType requestType, OpenApiSpecContext context) {
        if (!hasMatchHeaders(requestType, context)) {
            return Stream.empty();
        }

        var unsatisfiedVersionProblem = ProblemDetail.base(context)
                .subType(
                        "unsatisfied-version",
                        type("https://contentgrid.cloud/problems/unsatisfied-version"),
                        status(412),
                        requiredProperty("actual_version", new JsonSchemaString())
                );

        return Stream.of(Map.entry(
                HttpStatusCode.of(412),
                context.spec().getComponents().getResponses().register("preconditionFailed", () -> new OpenApiResponse()
                        .setDescription("If-Match or If-None-Match precondition failed")
                        .content(body -> body.addMediaType("application/problem+json", unsatisfiedVersionProblem.getSchema())))
        ));
    }
}
