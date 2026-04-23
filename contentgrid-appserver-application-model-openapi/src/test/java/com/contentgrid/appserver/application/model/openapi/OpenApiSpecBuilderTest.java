package com.contentgrid.appserver.application.model.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiMediaTypes.MediaType;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation.HttpStatusCode;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.OpenApiPathItem;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiSpec;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaArray;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaBoolean;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaInteger;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaNull;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaNumber;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaObject;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaOneOf;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaString.Format;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation.RelationEndPoint;
import com.contentgrid.appserver.application.model.relations.SourceOneToOneRelation;
import com.contentgrid.appserver.application.model.sortable.SortableField;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.application.model.values.AttributeName;
import com.contentgrid.appserver.application.model.values.ColumnName;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.application.model.values.FilterName;
import com.contentgrid.appserver.application.model.values.LinkName;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.application.model.values.PropertyPath;
import com.contentgrid.appserver.application.model.values.RelationName;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.application.model.values.SortableName;
import com.contentgrid.appserver.application.model.values.TableName;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenApiSpecBuilderTest {

    public static final OpenApiReference<JsonSchema> LINK_REFERENCE =
            new OpenApiReference<>("#/components/schemas/Link", null);

    private OpenApiSpec createSpec(Entity entity) {
        return OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(entity)
                .build());
    }

    private static OpenApiOperation path(OpenApiSpec spec, String method, String path) {
        var item = spec.getPaths().getItems().get(path);
        assertThat(item).as("path '%s' should exist", path)
                .isNotNull().isInstanceOf(OpenApiPathItem.class);
        var op = ((OpenApiPathItem) item).getOperations().get(HttpMethod.valueOf(method.toUpperCase()));
        assertThat(op).as("%s %s should exist", method, path).isNotNull();
        return op;
    }

    @Test
    void singleEntityBaseOperations() {
        var spec = createSpec(Entity.builder()
                .name(EntityName.of("test-entity"))
                .pathSegment(PathSegmentName.of("test-entity-collection-path"))
                .linkName(LinkName.of("__unused__"))
                .table(TableName.of("__unused__"))
                .build());

        assertThat(path(spec, "get", "/test-entity-collection-path")).satisfies(collectionOperation -> {
            assertThat(collectionOperation.getRequestBody()).isNull();

            assertThat(collectionOperation.getParameters())
                    .map(OpenApiPotentialReference::getOriginalObject)
                    .filteredOn(p -> p.getIn() == In.QUERY)
                    .allSatisfy(parameter -> assertThat(parameter.isRequired()).isFalse())
                    .map(OpenApiParameter::getName)
                    .containsExactlyInAnyOrder("_cursor", "_size");

            assertThat(collectionOperation.getResponse(200)).satisfies(collectionResponse -> {
                assertThat(collectionResponse.getContent().getJson().getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/test-entityCollection", null));
            });
        });

        assertThat(path(spec, "post", "/test-entity-collection-path")).satisfies(postOperation -> {
            assertThat(postOperation.getRequestBody()).satisfies(body -> {
                assertThat(body.isRequired()).isTrue();
                assertThat(body.getContent().getJson().getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/test-entityPostBody", null));
                assertThat(body.getContent().getMediaType(MediaType.APPLICATION_X_WWW_FORM_URLENCODED).getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/test-entityPostFormBody", null));
                assertThat(body.getContent().getMediaType(MediaType.MULTIPART_FORM_DATA).getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/test-entityPostMultipartFormDataBody", null));
            });

            assertThat(postOperation.getResponse(200)).isNull();
            assertThat(postOperation.getResponse(201)).satisfies(createdResponse -> {
                assertThat(createdResponse.getHeaders().getItems()).containsKey("Location");
            });
        });

        assertThat(path(spec, "get", "/test-entity-collection-path/{id}")).satisfies(itemOperation -> {
            assertThat(itemOperation.getRequestBody()).isNull();
            assertThat(itemOperation.getResponse(200)).satisfies(itemResponse -> {
                assertThat(itemResponse.getContent().getJson().getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/test-entityResponse", null));
            });
        });

        assertThat(path(spec, "put", "/test-entity-collection-path/{id}")).satisfies(putOperation -> {
            assertThat(putOperation.getRequestBody()).satisfies(body -> {
                assertThat(body.isRequired()).isTrue();
                assertThat(body.getContent().getJson().getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/test-entityPutBody", null));
            });
            assertThat(putOperation.getResponses())
                    .containsKey(HttpStatusCode.of(204))
                    .doesNotContainKey(HttpStatusCode.of(200));
        });

        assertThat(path(spec, "patch", "/test-entity-collection-path/{id}")).satisfies(patchOperation -> {
            assertThat(patchOperation.getRequestBody()).satisfies(body -> {
                assertThat(body.isRequired()).isTrue();
                assertThat(body.getContent().getJson().getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/test-entityPatchBody", null));
            });
            assertThat(patchOperation.getResponses())
                    .containsKey(HttpStatusCode.of(204))
                    .doesNotContainKey(HttpStatusCode.of(200));
        });

        assertThat(path(spec, "delete", "/test-entity-collection-path/{id}")).satisfies(deleteOperation -> {
            assertThat(deleteOperation.getRequestBody()).isNull();
            assertThat(deleteOperation.getResponse(200)).isNull();
            assertThat(deleteOperation.getResponse(204)).isNotNull();
        });

        assertThat(spec.getComponents().getSchemas().getItem("test-entityCollection"))
                .isEqualTo(new JsonSchemaObject()
                        .property("_embedded", new JsonSchemaObject()
                                .property("item", new JsonSchemaArray(
                                        new OpenApiReference<>("#/components/schemas/test-entityResponse", null))))
                        .requiredProperty("page",
                                new OpenApiReference<>("#/components/schemas/page", null)));

        assertThat(spec.getComponents().getSchemas().getItem("test-entityResponse"))
                .isEqualTo(new JsonSchemaObject()
                        .requiredProperty("id", new JsonSchemaString())
                        .requiredProperty("_links", new JsonSchemaObject()
                                .requiredProperty("self", LINK_REFERENCE)
                        )
                        .setTitle("test-entity")
                );

        assertThat(spec.getComponents().getSchemas().getItems()).containsOnlyKeys(
                "Link",
                "page",
                "test-entityResponse",
                "test-entityCollection",
                "test-entityPostBody",
                "test-entityPostFormBody",
                "test-entityPostFormBody",
                "test-entityPostMultipartFormDataBody",
                "test-entityPatchBody",
                "test-entityPatchFormBody",
                "test-entityPutBody",
                "test-entityPutFormBody",
                "problemDetail",
                "inputValidationProblemDetail",
                "genericInputValidationProblemDetail",
                "inputValidationTypeProblemDetail",
                "inputValidationTypeFormatProblemDetail",
                "inputValidationSimpleProblemDetail",
                "inputValidationDuplicateProblemDetail",
                "inputValidationAllowedValuesProblemDetail",
                "inputValidationPatternProblemDetail",
                "inputValidationMissingRelationTargetProblemDetail",
                "genericInvalidQueryParameterProblemDetail",
                "invalidQueryParameterFilterFormatProblemDetail",
                "invalidQueryParameterSortFormatProblemDetail",
                "invalidQueryParameterSortTargetProblemDetail",
                "invalidQueryParameterPaginationProblemDetail",
                "invalidRequestBodyProblemDetail",
                "invalidRequestHeaderProblemDetail",
                "integrityBlindRelationOverwriteProblemDetail",
                "integrityRequiredRelationProblemDetail"
        );
    }

    @Test
    void relations() {
        var spec = OpenApiSpecBuilder.convert(Application.builder()
                .name(ApplicationName.of("test-app"))
                .entity(Entity.builder()
                        .name(EntityName.of("relation-target"))
                        .pathSegment(PathSegmentName.of("relation-target-item-path"))
                        .linkName(LinkName.of("relation-target"))
                        .table(TableName.of("relation_target"))
                        .build())
                .entity(Entity.builder()
                        .name(EntityName.of("test-entity"))
                        .pathSegment(PathSegmentName.of("test-entity"))
                        .linkName(LinkName.of("test-entity"))
                        .table(TableName.of("test_entity"))
                        .build())
                .relation(SourceOneToOneRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("test-entity"))
                                .name(RelationName.of("one_to_one"))
                                .pathSegment(PathSegmentName.of("one-to-one"))
                                .linkName(LinkName.of("one_to_one"))
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("relation-target"))
                                .name(RelationName.of("test_entity_one_to_one"))
                                .pathSegment(PathSegmentName.of("test-entity-one-to-one"))
                                .linkName(LinkName.of("test_entity_one_to_one"))
                                .build())
                        .targetReference(ColumnName.of("one_to_one_id"))
                        .build())
                .relation(OneToManyRelation.builder()
                        .sourceEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("test-entity"))
                                .name(RelationName.of("one_to_many"))
                                .pathSegment(PathSegmentName.of("one-to-many"))
                                .linkName(LinkName.of("one_to_many"))
                                .build())
                        .targetEndPoint(RelationEndPoint.builder()
                                .entity(EntityName.of("relation-target"))
                                .name(RelationName.of("test_entity"))
                                .pathSegment(PathSegmentName.of("test-entity-one-to-many"))
                                .linkName(LinkName.of("test_entity"))
                                .build())
                        .sourceReference(ColumnName.of("test_entity_id"))
                        .build())
                        .relation(ManyToManyRelation.builder()
                                .sourceEndPoint(RelationEndPoint.builder()
                                        .entity(EntityName.of("test-entity"))
                                        .name(RelationName.of("many_to_many"))
                                        .pathSegment(PathSegmentName.of("many-to-many"))
                                        .linkName(LinkName.of("many_to_many"))
                                        .build())
                                .targetEndPoint(RelationEndPoint.builder()
                                        .entity(EntityName.of("relation-target"))
                                        .name(RelationName.of("test_entities"))
                                        .flag(HiddenEndpointFlag.INSTANCE)
                                        .build())
                                .joinTable(TableName.of("test_entity_self"))
                                .sourceReference(ColumnName.of("src_id"))
                                .targetReference(ColumnName.of("tgt_id"))
                                .build()
                        )
                .build());

        assertThat(List.of(
                path(spec, "get", "/test-entity/{id}/one-to-one"),
                path(spec, "get", "/test-entity/{id}/one-to-many/{itemId}")
        )).allSatisfy(operation -> {
            assertThat(operation.getResponse(200)).satisfies(response -> {
                assertThat(response.getContent().getMediatypes().get("application/json").getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/relation-targetResponse", null));
            });
        });
        assertThat(path(spec, "get", "/test-entity/{id}/one-to-many")).satisfies(operation -> {
            assertThat(operation.getResponse(200)).satisfies(response -> {
                assertThat(response.getContent().getMediatypes().get("application/json").getSchema())
                        .isEqualTo(new OpenApiReference<>("#/components/schemas/relation-targetCollection", null));
            });
        });

        assertThat(path(spec, "put", "/test-entity/{id}/one-to-one"))
                .satisfies(operation -> {
                    assertThat(operation.getRequestBody()).satisfies(body -> {
                        assertThat(body.isRequired()).isTrue();
                        assertThat(body.getContent().getMediatypes()).hasEntrySatisfying("text/uri-list", content -> {
                            assertThat(content.getSchema()).isEqualTo(
                                    new JsonSchemaString()
                                            .setFormat(Format.URI)
                                            .setExamples(
                                                    List.of("https://contentgrid-app.example/relation-target-item-path/00000000-0000-0000-0000-000000000000")));
                        });
                    });
                });
        assertThat(path(spec, "post", "/test-entity/{id}/one-to-many"))
                .satisfies(operation -> {
                    assertThat(operation.getRequestBody()).satisfies(body -> {
                        assertThat(body.isRequired()).isTrue();
                        assertThat(body.getContent().getMediatypes()).hasEntrySatisfying("text/uri-list", content -> {
                            assertThat(content.getSchema()).isEqualTo(new JsonSchemaArray(
                                    new JsonSchemaString()
                                            .setFormat(Format.URI)
                                            .setExamples(
                                                    List.of("https://contentgrid-app.example/relation-target-item-path/00000000-0000-0000-0000-000000000000"))
                            ));
                        });
                    });
                });

        assertThat(spec.getPaths().path("/test-entity/{id}/one-to-one").getOperations())
                .doesNotContainKey(HttpMethod.POST);
        assertThat(spec.getPaths().path("/test-entity/{id}/one-to-many").getOperations())
                .doesNotContainKeys(HttpMethod.PUT);

        assertThat(List.of(
                path(spec, "delete", "/test-entity/{id}/one-to-one"),
                path(spec, "delete", "/test-entity/{id}/one-to-many"),
                path(spec, "delete", "/test-entity/{id}/one-to-many/{itemId}")
        )).allSatisfy(operation -> {
            assertThat(operation.getRequestBody()).isNull();
            assertThat(operation.getResponse(204)).isNotNull();
        });

        assertThat(spec.getComponents().getSchemas().getItem("test-entityResponse"))
                .isEqualTo(new JsonSchemaObject()
                        .requiredProperty("id", new JsonSchemaString())
                        .requiredProperty("_links", new JsonSchemaObject()
                                .requiredProperty("self", LINK_REFERENCE))
                        .setTitle("test-entity")
                );
    }

    @Test
    void attributes() {
        var spec = createSpec(Entity.builder()
                .name(EntityName.of("test-entity"))
                .pathSegment(PathSegmentName.of("test-entities"))
                .linkName(LinkName.of("test-entity"))
                .table(TableName.of("test_entity"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("plain")).column(ColumnName.of("plain"))
                        .type(Type.TEXT).build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("required")).column(ColumnName.of("required"))
                        .type(Type.TEXT).constraint(Constraint.required()).build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("read_only")).column(ColumnName.of("read_only"))
                        .type(Type.TEXT).flag(ReadOnlyFlag.INSTANCE).build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("search_only"))
                        .attributePath(new SimpleAttributePath(AttributeName.of("required")))
                        .operation(Operation.EXACT)
                        .build())
                .sortableField(SortableField.builder()
                        .name(SortableName.of("sort_only"))
                        .propertyPath(new SimpleAttributePath(AttributeName.of("required")))
                        .build())
                .build());

        assertThat(path(spec, "get", "/test-entities")).satisfies(collectionOperation -> {
            assertThat(collectionOperation.getParameters())
                    .map(OpenApiPotentialReference::getOriginalObject)
                    .filteredOn(p -> p.getIn() == In.QUERY)
                    .allSatisfy(parameter -> assertThat(parameter.isRequired()).isFalse())
                    .map(OpenApiParameter::getName)
                    .contains("search_only")
                    .doesNotContain("plain", "required", "read_only");

            assertThat(collectionOperation.getParameters())
                    .map(OpenApiPotentialReference::getOriginalObject)
                    .filteredOn(p -> p.getIn() == In.QUERY && p.getName().equals("_sort"))
                    .singleElement()
                    .satisfies(parameter -> {
                        assertThat(parameter.isRequired()).isFalse();
                        assertThat(parameter.getSchema()).isInstanceOfSatisfying(JsonSchemaArray.class,
                                dataType -> {
                                    assertThat(dataType.getItems()).isInstanceOfSatisfying(JsonSchemaEnum.class,
                                            enumDt -> {
                                                assertThat(enumDt.getEnum())
                                                        .containsExactly("sort_only,asc", "sort_only,desc");
                                            });
                                });
                    });
        });

        assertThat(spec.getComponents().getSchemas().getItems())
                .extractingByKey("test-entityResponse")
                .isEqualTo(new JsonSchemaObject()
                        .requiredProperty("id", new JsonSchemaString())
                        .requiredProperty("plain",
                                new JsonSchemaOneOf(List.of(new JsonSchemaString(), new JsonSchemaNull())))
                        .requiredProperty("required", new JsonSchemaString())
                        .requiredProperty("read_only",
                                new JsonSchemaOneOf(
                                        List.of(new JsonSchemaString(), new JsonSchemaNull())))
                        .requiredProperty("_links", new JsonSchemaObject()
                                .requiredProperty("self", LINK_REFERENCE))
                        .setTitle("test-entity")
                );

        assertThat(spec.getComponents().getSchemas().getItems())
                .extractingByKey("test-entityPostBody")
                .isEqualTo(new JsonSchemaObject()
                        .property("plain",
                                new JsonSchemaOneOf(List.of(new JsonSchemaString(), new JsonSchemaNull())))
                        .requiredProperty("required", new JsonSchemaString())
                        .setTitle("test-entity")
                );

        assertThat(spec.getComponents().getSchemas().getItems())
                .extractingByKey("test-entityPutBody")
                .isEqualTo(new JsonSchemaObject()
                        .property("plain",
                                new JsonSchemaOneOf(List.of(new JsonSchemaString(), new JsonSchemaNull())))
                        .requiredProperty("required", new JsonSchemaString())
                        .setTitle("test-entity")
                );

        assertThat(spec.getComponents().getSchemas().getItems())
                .extractingByKey("test-entityPatchBody")
                .isEqualTo(new JsonSchemaObject()
                        .property("plain",
                                new JsonSchemaOneOf(List.of(new JsonSchemaString(), new JsonSchemaNull())))
                        .property("required", new JsonSchemaString())
                        .setTitle("test-entity")
                );
    }

    static Stream<Arguments> attributeTypes() {
        return Stream.of(
                Arguments.of(Type.TEXT, new JsonSchemaString()),
                Arguments.of(Type.DATE, new JsonSchemaString().setFormat(Format.DATE)),
                Arguments.of(Type.DATETIME, new JsonSchemaString().setFormat(Format.DATE_TIME)),
                Arguments.of(Type.BOOLEAN, new JsonSchemaBoolean()),
                Arguments.of(Type.DOUBLE, new JsonSchemaNumber()),
                Arguments.of(Type.LONG, new JsonSchemaInteger())
        );
    }

    @ParameterizedTest
    @MethodSource
    void attributeTypes(Type type, JsonSchema jsonSchemaObject) {
        var spec = createSpec(Entity.builder()
                .name(EntityName.of("test-entity"))
                .pathSegment(PathSegmentName.of("test-entities"))
                .linkName(LinkName.of("test-entity"))
                .table(TableName.of("test_entity"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("required_attr")).column(ColumnName.of("required_attr"))
                        .type(type).constraint(Constraint.required()).build())
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("optional_attr")).column(ColumnName.of("optional_attr"))
                        .type(type).build())
                .build());

        assertThat(spec.getComponents().getSchemas().getItems())
                .extractingByKey("test-entityResponse")
                .isInstanceOfSatisfying(JsonSchemaObject.class, response -> {
                    assertThat(response.getProperties().get("id")).isEqualTo(new JsonSchemaString());
                    assertThat(response.getProperties().get("required_attr")).isEqualTo(jsonSchemaObject);
                    assertThat(response.getProperties().get("optional_attr")).isEqualTo(new JsonSchemaOneOf(
                            List.<OpenApiPotentialReference<JsonSchema>>of(jsonSchemaObject, new JsonSchemaNull())));
                });
    }

    @Test
    void queryParameters() {
        var spec = createSpec(Entity.builder()
                .name(EntityName.of("test-entity"))
                .pathSegment(PathSegmentName.of("test-entities"))
                .linkName(LinkName.of("test-entity"))
                .table(TableName.of("test_entity"))
                .attribute(SimpleAttribute.builder()
                        .name(AttributeName.of("name")).column(ColumnName.of("name"))
                        .type(Type.TEXT).build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("name"))
                        .attributePath(PropertyPath.of(AttributeName.of("name")))
                        .operation(Operation.EXACT)
                        .build())
                .searchFilter(AttributeSearchFilter.builder()
                        .name(FilterName.of("name_hidden"))
                        .attributePath(PropertyPath.of(AttributeName.of("name")))
                        .operation(Operation.EXACT)
                        .flag(HiddenSearchFilterFlag.INSTANCE)
                        .build())
                .build());

        assertThat(path(spec, "get", "/test-entities")).satisfies(collectionOperation -> {
            assertThat(collectionOperation.getParameters())
                    .map(OpenApiPotentialReference::getOriginalObject)
                    .allSatisfy(parameter -> assertThat(parameter.isRequired()).isFalse())
                    .filteredOn(p -> p.getIn() == In.QUERY)
                    .extracting("name")
                    .contains("name")
                    .doesNotContain("name_hidden");
        });
    }

    @Test
    void contentAttribute() {
        var spec = createSpec(Entity.builder()
                .name(EntityName.of("test-entity"))
                .pathSegment(PathSegmentName.of("test-entities"))
                .linkName(LinkName.of("test-entity"))
                .table(TableName.of("test_entity"))
                .attribute(ContentAttribute.builder()
                        .name(AttributeName.of("content"))
                        .pathSegment(PathSegmentName.of("content-path"))
                        .linkName(LinkName.of("content-link"))
                        .idColumn(ColumnName.of("content_id"))
                        .lengthColumn(ColumnName.of("content_length"))
                        .mimetypeColumn(ColumnName.of("content_mimetype"))
                        .filenameColumn(ColumnName.of("content_filename"))
                        .build()
                )
                .build());

        // GET response
        assertThat(spec.getComponents().getSchemas().getItem("test-entityResponse")).isInstanceOfSatisfying(JsonSchemaObject.class, jsonSchemaObject -> {
            assertThat(jsonSchemaObject.getProperties().get("id")).isEqualTo(new JsonSchemaString());
            assertThat(jsonSchemaObject.getProperties().get("content")).isInstanceOfSatisfying(JsonSchemaOneOf.class, oneOf -> {
                assertThat(oneOf.getOneOf())
                        .satisfiesExactlyInAnyOrder(
                                content -> assertThat(content.getOriginalObject()).isInstanceOfSatisfying(JsonSchemaObject.class, object -> {
                                    assertThat(object.getProperties())
                                            .containsOnlyKeys("length", "mimetype", "filename");
                                }),
                                nullObject -> assertThat(nullObject).isInstanceOf(JsonSchemaNull.class)
                        );
            });

        });

        // POST bodies
        assertThat(List.of(
                spec.getComponents().getSchemas().getItem("test-entityPostBody"),
                spec.getComponents().getSchemas().getItem("test-entityPostFormBody")
        )).allSatisfy(schema -> {
            assertThat(schema).isInstanceOfSatisfying(JsonSchemaObject.class, jsonSchemaObject -> {
                assertThat(jsonSchemaObject.getProperties())
                        .doesNotContainKeys("content", "content.mimetype", "content.filename");
            });
        });

        assertThat(spec.getComponents().getSchemas().getItem("test-entityPostMultipartFormDataBody"))
                .isInstanceOfSatisfying(JsonSchemaObject.class, jsonSchemaObject -> {
                    assertThat(jsonSchemaObject.getProperties().get("content")).isInstanceOfSatisfying(
                            JsonSchemaString.class, string -> {
                                assertThat(string.getFormat()).isEqualTo(Format.BINARY);
                            });
                });

        // PUT & PATCH bodies
        assertThat(List.of(
                spec.getComponents().getSchemas().getItem("test-entityPutBody"),
                spec.getComponents().getSchemas().getItem("test-entityPatchBody")
        )).allSatisfy(schema -> {
            assertThat(schema).isInstanceOfSatisfying(JsonSchemaObject.class, jsonSchemaObject -> {
                assertThat(jsonSchemaObject.getProperties().get("content")).isInstanceOfSatisfying(JsonSchemaOneOf.class, oneOf -> {
                    assertThat(oneOf.getOneOf())
                            .satisfiesExactlyInAnyOrder(
                                    content -> assertThat(content.getOriginalObject()).isInstanceOfSatisfying(JsonSchemaObject.class, object -> {
                                        assertThat(object.getProperties())
                                                .containsOnlyKeys("mimetype", "filename");
                                    }),
                                    nullObject -> assertThat(nullObject).isInstanceOf(JsonSchemaNull.class)
                            );
                });
            });
        });

        assertThat(List.of(
                spec.getComponents().getSchemas().getItem("test-entityPutFormBody"),
                spec.getComponents().getSchemas().getItem("test-entityPatchFormBody")
        )).allSatisfy(schema -> {
            assertThat(schema).isInstanceOfSatisfying(JsonSchemaObject.class, jsonSchemaObject -> {
                assertThat(jsonSchemaObject.getProperties())
                        .doesNotContainKeys("content", "content.length")
                        .containsKeys("content.mimetype", "content.filename");
            });
        });
    }
}