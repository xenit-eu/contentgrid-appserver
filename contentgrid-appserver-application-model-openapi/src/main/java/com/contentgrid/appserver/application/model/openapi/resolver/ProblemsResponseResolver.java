package com.contentgrid.appserver.application.model.openapi.resolver;

import static com.contentgrid.appserver.application.model.openapi.ProblemDetailsJsonSchema.ProblemDetailsCustomizer.description;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetailsJsonSchema.ProblemDetailsCustomizer.property;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetailsJsonSchema.ProblemDetailsCustomizer.requiredProperty;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetailsJsonSchema.ProblemDetailsCustomizer.status;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetailsJsonSchema.ProblemDetailsCustomizer.title;
import static com.contentgrid.appserver.application.model.openapi.ProblemDetailsJsonSchema.ProblemDetailsCustomizer.type;

import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.ProblemDetailsJsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation.HttpStatusCode;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiResponse;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaArray;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaConst;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaOneOf;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString.Format;
import com.contentgrid.appserver.application.model.openapi.type.AttributeType;
import com.contentgrid.appserver.application.model.openapi.type.CollectionType;
import com.contentgrid.appserver.application.model.openapi.type.EntityType;
import com.contentgrid.appserver.application.model.openapi.type.HttpRequestType;
import com.contentgrid.appserver.application.model.openapi.type.RelationItemType;
import com.contentgrid.appserver.application.model.openapi.type.RelationType;
import com.contentgrid.appserver.application.model.openapi.type.SemanticType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Maps most error responses to operations.
 * <p>
 * There is mapping from method + {@link EndpointType} to a set of problems in {@link ProblemSet},
 * which determines what problems belong to which endpoints.
 */
public class ProblemsResponseResolver implements ResponseResolver{

    /**
     * Identifies the type of the endpoint identified by a semantic type
     */
    @RequiredArgsConstructor
    enum EndpointType {
        RELATION_ITEM(t -> t instanceof RelationItemType),
        ENTITY_COLLECTION(t -> t instanceof CollectionType ct && ct.getElementType() instanceof EntityType),
        ENTITY_ITEM(t -> t instanceof EntityType),
        CONTENT(t -> t instanceof AttributeType.ContentAttributeType),
        RELATION_TO_ONE(t -> t instanceof RelationType rt && ENTITY_ITEM.matches(rt.getTarget())),
        RELATION_TO_MANY(t -> t instanceof RelationType rt && ENTITY_COLLECTION.matches(rt.getTarget())),
        UNKNOWN(t -> true)
        ;
        private final Predicate<SemanticType> matcher;

        private boolean matches(SemanticType semanticType) {
            return matcher.test(semanticType);
        }

        public static EndpointType identify(SemanticType semanticType) {
            for (var value : values()) {
                if(value.matches(semanticType)) {
                    return value;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * Links a set of problem details to method + endpoint type
     */
    @RequiredArgsConstructor
    enum ProblemSet {
        INPUT_VALIDATION(
                HttpStatusCode.of(400),
                Matcher.compose(
                        Matcher.matcher(Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH), EndpointType.ENTITY_ITEM),
                        Matcher.matcher(HttpMethod.PUT, EndpointType.RELATION_TO_ONE),
                        Matcher.matcher(HttpMethod.POST, EndpointType.RELATION_TO_MANY)
        )),
        QUERY_PARAMETER(
                HttpStatusCode.of(400),
                Matcher.matcher(HttpMethod.GET, EndpointType.ENTITY_COLLECTION)
        ),
        REQUEST_PROBLEM_HEADER(HttpStatusCode.of(400), Matcher.always()),
        REQUEST_PROBLEM_BODY(HttpStatusCode.of(400), (m, e) -> m != HttpMethod.GET && m != HttpMethod.DELETE),

        NOT_FOUND_ENTITY_ITEM(
                HttpStatusCode.of(404),
                Matcher.compose(
                        (m, e) -> m != HttpMethod.POST && e == EndpointType.ENTITY_ITEM,
                        Matcher.allOnEndpoint(EndpointType.CONTENT),
                        Matcher.allOnEndpoint(EndpointType.RELATION_TO_MANY),
                        Matcher.allOnEndpoint(EndpointType.RELATION_TO_ONE),
                        Matcher.allOnEndpoint(EndpointType.RELATION_ITEM)
                )),
        NOT_FOUND_RELATION_ITEM(
                HttpStatusCode.of(404),
                Matcher.compose(
                        Matcher.matcher(Set.of(HttpMethod.GET, HttpMethod.DELETE), EndpointType.RELATION_TO_ONE),
                        Matcher.matcher(Set.of(HttpMethod.GET, HttpMethod.DELETE), EndpointType.RELATION_ITEM)
                )),
        INTEGRITY_BLIND_RELATION_OVERWRITE(
                HttpStatusCode.of(409),
                Matcher.compose(
                Matcher.matcher(HttpMethod.PUT, EndpointType.RELATION_TO_ONE),
                Matcher.matcher(HttpMethod.POST, EndpointType.RELATION_TO_MANY),
                Matcher.matcher(HttpMethod.POST, EndpointType.ENTITY_ITEM)
        )),
        INTEGRITY_REQUIRED_RELATION(
                HttpStatusCode.of(409),
                Matcher.compose(
                        Matcher.matcher(HttpMethod.DELETE, EndpointType.ENTITY_ITEM),
                        Matcher.matcher(HttpMethod.DELETE, EndpointType.RELATION_TO_ONE),
                        Matcher.matcher(HttpMethod.DELETE, EndpointType.RELATION_TO_MANY),
                        Matcher.matcher(HttpMethod.DELETE, EndpointType.RELATION_ITEM)
                ))
        ;

        @Getter
        private final HttpStatusCode statusCode;
        private final Matcher matcher;

        static EnumSet<ProblemSet> identifySets(HttpRequestType requestType) {
            var set = EnumSet.allOf(ProblemSet.class);

            var endpointType = EndpointType.identify(requestType.getType());
            set.removeIf(ps -> !ps.matcher.match(requestType.getMethod(), endpointType));

            return set;
        }

        interface Matcher {
            boolean match(HttpMethod method, EndpointType type);

            static Matcher matcher(HttpMethod method, EndpointType endpointType) {
                return (m, t) -> t == endpointType && m == method;
            }

            static Matcher matcher(Set<HttpMethod> methods, EndpointType endpointType) {
                return compose(methods.stream()
                        .map(m -> matcher(m, endpointType))
                        .toList()
                );
            }

            static Matcher allOnEndpoint(EndpointType endpointType) {
                return (m, t) -> t == endpointType;
            }

            static Matcher compose(Matcher... matchers) {
                return compose(Arrays.asList(matchers));
            }

            static Matcher compose(Collection<Matcher> matchers) {
                return (m, t) -> matchers.stream()
                        .anyMatch(matcher -> matcher.match(m, t));
            }

            static Matcher always() {
                return (m, t) -> true;
            }
        }
    }

    @Override
    public Stream<Entry<HttpStatusCode, OpenApiPotentialReference<OpenApiResponse>>> resolveResponse(
            HttpRequestType requestType, OpenApiSpecContext context) {

        var problemSets = ProblemSet.identifySets(requestType);

        var statusCodes = problemSets.stream()
                .map(ProblemSet::getStatusCode)
                .collect(Collectors.toSet());

        var responses = new LinkedHashMap<HttpStatusCode, OpenApiPotentialReference<OpenApiResponse>>();

        for (var statusCode : statusCodes) {
            responses.put(statusCode, new OpenApiResponse()
                    .content(mt -> mt.addMediaType(
                            "application/problem+json",
                            createResponseBody(context, statusCode, problemSets)
                    ))
            );
        }

        return responses.entrySet().stream();
    }

    private OpenApiPotentialReference<JsonSchema> createResponseBody(OpenApiSpecContext context, HttpStatusCode statusCode, Set<ProblemSet> problemSets) {
        var problemTypes = new ArrayList<OpenApiPotentialReference<JsonSchema>>();
        for(var problemSet: problemSets) {
            if (!Objects.equals(problemSet.getStatusCode(), statusCode)) {
                continue;
            }
            problemTypes.add(switch (problemSet) {
                case INPUT_VALIDATION -> createInputValidationProblem(context);
                case QUERY_PARAMETER -> createQueryParameterProblem(context);
                case REQUEST_PROBLEM_BODY -> ProblemDetailsJsonSchema.base(context)
                        .subType("invalid-request-body",
                                type(
                                        "https://contentgrid.cloud/problems/invalid-request/body",
                                        "https://contentgrid.cloud/problems/invalid-request/body/json",
                                        "https://contentgrid.cloud/problems/invalid-request/body/uri-list",
                                        "https://contentgrid.cloud/problems/invalid-request/body/single-link"
                                ),
                                title("Invalid request body"),
                                status(400)
                        );
                case REQUEST_PROBLEM_HEADER -> ProblemDetailsJsonSchema.base(context)
                        .subType("invalid-request-header",
                                type(
                                        "https://contentgrid.cloud/problems/invalid-request/required-header",
                                        "https://contentgrid.cloud/problems/invalid-request/forbidden-header",
                                        "https://contentgrid.cloud/problems/invalid-request/invalid-header"
                                ),
                                title("Invalid request header"),
                                status(400),
                                requiredProperty("header", new JsonSchemaString())
                        );
                case NOT_FOUND_ENTITY_ITEM -> createNotFoundProblem(context, "entity-item");
                case NOT_FOUND_RELATION_ITEM -> createNotFoundProblem(context, "relation-item");
                case INTEGRITY_BLIND_RELATION_OVERWRITE -> ProblemDetailsJsonSchema.base(context)
                        .subType("integrity.blind-relation-overwrite",
                                type("https://contentgrid.cloud/problems/integrity/blind-relation-overwrite"),
                                status(409),
                                title("Overwrite of an existing relation blocked"),
                                description("In a to-one relation, the target entity is already referenced by another entity"),
                                requiredProperty("new_item", new JsonSchemaString().setFormat(Format.URI)),
                                requiredProperty("new_relation", new JsonSchemaString().setFormat(Format.URI)),
                                requiredProperty("existing_item", new JsonSchemaString().setFormat(Format.URI)),
                                requiredProperty("existing_relation", new JsonSchemaString().setFormat(Format.URI)),
                                requiredProperty("target_item", new JsonSchemaString().setFormat(Format.URI)),
                                property("target_relation", new JsonSchemaString().setFormat(Format.URI)),
                                requiredProperty("additional_errors", JsonSchemaArray::new)
                        );
                case INTEGRITY_REQUIRED_RELATION -> ProblemDetailsJsonSchema.base(context)
                        .subType("required-relation",
                                type("https://contentgrid.cloud/problems/integrity/required-relation"),
                                title("Item is still referenced by a required relation"),
                                requiredProperty("affected_relation", new JsonSchemaString().setFormat(Format.URI))
                        );
            });
        }

        return switch (problemTypes.size()) {
            case 0 -> null;
            case 1 -> problemTypes.getFirst();
            default-> new JsonSchemaOneOf(problemTypes);
        };
    }

    private OpenApiPotentialReference<JsonSchema> createInputValidationProblem(OpenApiSpecContext context) {
        var inputValidationField = ProblemDetailsJsonSchema.base(context)
                .baseType("input-validation.field",
                        requiredProperty("field", new JsonSchemaString()
                                .setDescription("Refers to the specific input field for which validation failed with a property path")
                        )
                )
                .andSubType("type",
                        type("https://contentgrid.cloud/problems/input/validation/type"),
                        title("Wrong type"),
                        description("Submitted value is of the wrong type"),
                        requiredProperty("expected_type", new JsonSchemaString()),
                        requiredProperty("actual_type", new JsonSchemaString())
                )
                .andSubType("type-format",
                        type("https://contentgrid.cloud/problems/input/validation/type/format"),
                        title("Wrong format"),
                        description("Submitted value is of the correct type, but doesn't match the expected format"),
                        requiredProperty("expected_type", new JsonSchemaString()),
                        requiredProperty("format_error", new JsonSchemaString())
                )
                .andSubType("noContent",
                        type("https://contentgrid.cloud/problems/input/validation/no-content"),
                        title("Content attribute is empty"),
                        description("A content attribute can not be set when there is no content")
                )
                .andSubType("required",
                        type("https://contentgrid.cloud/problems/input/validation/required"),
                        title("Required field"),
                        description("This field is required")
                )
                .andSubType("duplicate",
                        type("https://contentgrid.cloud/problems/input/validation/duplicate"),
                        title("Duplicate value"),
                        description("This field value is already used by another entity item"),
                        requiredProperty("conflicting_item", new JsonSchemaString().setFormat(Format.URI))
                )
                .andSubType("allowed-values",
                        type("https://contentgrid.cloud/problems/input/validation/allowed-values"),
                        title("Value not allowed"),
                        description("This field can only be set to one of the allowed values"),
                        requiredProperty("allowed_values", new JsonSchemaArray(new JsonSchemaString()))
                )
                .andSubType("pattern",
                        type("https://contentgrid.cloud/problems/input/validation/pattern"),
                        title("Value does not match pattern"),
                        description("The value does not match the required pattern"),
                        requiredProperty("pattern", new JsonSchemaString())
                )
                .andSubType("missing-relation-target",
                        type("https://contentgrid.cloud/problems/input/validation/missing-relation-target"),
                        title("Relation target does not exist"),
                        description("The referenced entity-item does not exist"),
                        requiredProperty("missing_item", new JsonSchemaString().setFormat(Format.URI))
                )
                .composite();

        return ProblemDetailsJsonSchema.base(context).subType(
                "input-validation",
                type("https://contentgrid.cloud/problems/input/validation"),
                title("Input validation failed"),
                description("The submitted data does not meet the requirements"),
                status(400),
                requiredProperty("errors",
                        new JsonSchemaArray(inputValidationField)
                                .setDescription("List of validation errors")
                )
        );
    }

    private OpenApiPotentialReference<JsonSchema> createQueryParameterProblem(OpenApiSpecContext context) {
        return ProblemDetailsJsonSchema.base(context)
                .baseType("invalid-query-parameter",
                        title("A query parameter is not valid"),
                        status(400),
                        requiredProperty("query_parameter", new JsonSchemaString())
                )
                .andSubType("filter-format",
                        type("https://contentgrid.cloud/problems/invalid-query-parameter/filter/format"),
                        title("Filter value is of wrong format"),
                        description("The value of a filter does not match the expected format"),
                        requiredProperty("expected_type", new JsonSchemaString()),
                        requiredProperty("format_error", new JsonSchemaString()),
                        requiredProperty("additional_errors", JsonSchemaArray::new)
                )
                .andSubType("sort-format",
                        type("https://contentgrid.cloud/problems/invalid-query-parameter/sort/format"),
                        title("The sort parameter is malformed"),
                        requiredProperty("query_parameter", new JsonSchemaConst("_sort")),
                        requiredProperty("format_error", new JsonSchemaString())
                )
                .andSubType("sort-target",
                        type("https://contentgrid.cloud/problems/invalid-query-parameter/sort/target"),
                        title("Invalid sort target"),
                        description("The sort parameter refers to a target that doesn't exist or on which sorting is not possible"),
                        requiredProperty("query_parameter", new JsonSchemaConst("_sort")),
                        requiredProperty("target_name", new JsonSchemaString())
                )
                .andSubType("pagination",
                        type("https://contentgrid.cloud/problems/invalid-query-parameter/pagination"),
                        title("Invalid pagination parameter"),
                        requiredProperty("query_parameter", new JsonSchemaEnum(List.of("_limit", "_cursor"))),
                        requiredProperty("format_error", new JsonSchemaString())
                )
                .composite();
    }

    private OpenApiPotentialReference<JsonSchema> createNotFoundProblem(OpenApiSpecContext context, String type) {
        return ProblemDetailsJsonSchema.base(context)
                .baseType("not-found",
                        status(404)
                )
                .subType(type,
                        type("https://contentgrid.cloud/problems/not-found/"+type)
                );
    }
}
