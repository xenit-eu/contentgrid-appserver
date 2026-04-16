package com.contentgrid.appserver.application.model.openapi;

import static com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType.FORM;
import static com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType.JSON;
import static com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType.MULTIPART_FORM;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiInfo;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiMediaTypes.MediaType;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiOperation;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.HttpMethod;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.OpenApiPathItem;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiPotentialReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiSpec;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiTag;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.AbstractJsonSchemaDataType;
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
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ArrayBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper.Context;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyType;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ContentBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ObjectBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.RelationBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SimpleBodyValue;
import com.contentgrid.appserver.application.model.openapi.type.AttributeType;
import com.contentgrid.appserver.application.model.openapi.type.CollectionType;
import com.contentgrid.appserver.application.model.openapi.type.CompositeRequestParameterResolver;
import com.contentgrid.appserver.application.model.openapi.type.CompositeResponseHeaderResolver;
import com.contentgrid.appserver.application.model.openapi.type.EntityType;
import com.contentgrid.appserver.application.model.openapi.type.HttpRequestType;
import com.contentgrid.appserver.application.model.openapi.type.HttpResponseType;
import com.contentgrid.appserver.application.model.openapi.type.CollectionPaginationQueryParameterResolver;
import com.contentgrid.appserver.application.model.openapi.type.CollectionSearchQueryParameterResolver;
import com.contentgrid.appserver.application.model.openapi.type.ResponseHeaderResolver;
import com.contentgrid.appserver.application.model.openapi.type.VersioningHeadersResolver;
import com.contentgrid.appserver.application.model.openapi.type.RequestParameterResolver;
import com.contentgrid.appserver.application.model.openapi.type.SemanticType;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class OpenApiSpecBuilder {

    private static final RequestParameterResolver PARAMETER_RESOLVER;
    private static final ResponseHeaderResolver RESPONSE_HEADER_RESOLVER;

    public static final OpenApiParameter ENTITY_ID_PARAM = new OpenApiParameter("id", In.PATH);

    static {
        List<RequestParameterResolver> parameterResolvers = new ArrayList<>();
        List<ResponseHeaderResolver> responseHeaderResolvers = new ArrayList<>();

        PARAMETER_RESOLVER = new CompositeRequestParameterResolver(parameterResolvers);
        RESPONSE_HEADER_RESOLVER = new CompositeResponseHeaderResolver(responseHeaderResolvers);

        parameterResolvers.add(new CollectionSearchQueryParameterResolver((bv, c) -> bodyValueToJsonSchema(c, bv)));
        parameterResolvers.add(new CollectionPaginationQueryParameterResolver());
        parameterResolvers.add(new VersioningHeadersResolver());

        responseHeaderResolvers.add(new VersioningHeadersResolver());
    }


    public static OpenApiSpec convert(Application application) {
        var spec = new OpenApiSpec("3.2.0", new OpenApiInfo(application.getName().getValue(), null, null, "1.0"));
        var context = new OpenApiSpecContext(application, spec);
        for (var entity : application.getEntities()) {
            addEntity(context, entity.getName());
        }
        return spec;
    }

    private static void addEntity(OpenApiSpecContext context, EntityName entityName) {
        var entity = context.application().getRequiredEntityByName(entityName);
        var tag = new OpenApiTag(entityName.getValue())
                .setDescription(entity.getTranslations(Locale.ROOT).getDescription())
                .setKind("nav");
        context.spec().getTags().add(tag);

        var semanticType = new EntityType(entityName);

        // collection
        context.spec().getPaths().path("/"+entity.getPathSegment().getValue())
                .method(HttpMethod.GET, op -> {
                    var collectionType = new CollectionType(semanticType);
                    op.setTags(List.of(tag.getName()))
                            .response(200, resp -> {
                                resp.getContent().addJson(resolveCollectionSchema(entityName, context));
                            });
                    // TODO add error responses
                    addResolved(context, HttpMethod.GET, op, collectionType);
                })
                .method(HttpMethod.POST, op -> {
                    op
                            .requestBody(body -> {
                                body.getContent().addMediaType(MediaType.APPLICATION_JSON, resolveItemSchema(entityName, context, BodyType.POST, JSON));
                                body.getContent().addMediaType(MediaType.APPLICATION_X_WWW_FORM_URLENCODED, resolveItemSchema(entityName, context, BodyType.POST, FORM));
                                body.getContent().addMediaType(MediaType.MULTIPART_FORM_DATA, resolveItemSchema(entityName, context, BodyType.POST, MULTIPART_FORM));
                                body.setRequired(true);
                            })
                            .response(201, resp -> {
                                resp.getHeaders().header("Location", h -> h
                                        .setRequired(true));
                            });
                    addResolved(context, HttpMethod.POST, op, semanticType);
                            // TODO: add error responses
                });

        // item
        context.spec().getPaths().path("/"+entity.getPathSegment().getValue()+"/{id}")
                .setParameters(List.of(ENTITY_ID_PARAM))
                .method(HttpMethod.GET, op -> {
                    op.response(200, resp -> {
                        resp.getContent().addJson(resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON));
                    });
                            // TODO: add error responses
                })
                .method(HttpMethod.PUT, op -> {
                    op.requestBody(body -> {
                                body.getContent().addJson(resolveItemSchema(entityName, context, BodyType.PUT, JSON));
                                body.getContent().addMediaType(MediaType.APPLICATION_X_WWW_FORM_URLENCODED, resolveItemSchema(entityName, context, BodyType.PUT, FORM));
                                body.setRequired(true);
                            })
                            .response(204, resp -> {
                            });
                    // TODO: add error response
                })
                .method(HttpMethod.PATCH, op -> {
                    op.requestBody(body -> {
                                body.getContent().addJson(resolveItemSchema(entityName, context, BodyType.PATCH, JSON));
                                body.getContent().addMediaType(MediaType.APPLICATION_X_WWW_FORM_URLENCODED, resolveItemSchema(entityName, context, BodyType.PATCH, FORM));
                                body.setRequired(true);
                            })
                            .response(204, resp -> {
                            });
                    // TODO: add error response
                })
                .method(HttpMethod.DELETE, op -> {
                    op.response(204, resp -> {
                    });
                            // TODO: add error response
                })
                .each(((method, openApiOperation) -> {
                    openApiOperation.setTags(List.of(tag.getName()));
                    addResolved(context, method, openApiOperation, semanticType);
                }));

        for (var contentAttribute : entity.getContentAttributes()) {
            if(contentAttribute.hasFlag(IgnoredFlag.class)) {
                continue;
            }

            addContentAttribute(context, entity, contentAttribute);
        }

        for (var relation : context.application().getRelationsForSourceEntity(entity)) {
            if(relation.getSourceEndPoint().hasFlag(HiddenEndpointFlag.class)) {
                continue;
            }

            addRelation(context, entity, relation);
        }
    }

    private static void addContentAttribute(OpenApiSpecContext context, Entity entity,
            ContentAttribute contentAttribute) {
        var contentPathItem = context.spec().getComponents().getPathItems().register("content", () -> {
            var semanticType = AttributeType.of(contentAttribute);
            return new OpenApiPathItem()
                    .setParameters(List.of(ENTITY_ID_PARAM))
                    .method(HttpMethod.GET, op -> {
                        op.response(200, resp -> {
                            resp.setDescription("The contents of the stored file");
                            resp.getContent().addMediaType("*/*", new JsonSchemaString().setFormat(Format.BINARY));
                            resp.getHeaders()
                                    .header("Content-Disposition", h -> {
                                        h.setDescription("Content-Disposition header containing the filename");
                                        h.setRequired(true);
                                        h.setExample("attachment;filename=\"my-file.pdf\"");
                                    })
                                    .header("ETag", h -> h.setRequired(true));
                        });
                        // TODO: add error response
                    })
                    .method(HttpMethod.PUT, op -> {
                        op
                                .requestBody(body -> {
                                    body.setDescription("Update the stored file");
                                    body.getContent().addMediaType("*/*", new JsonSchemaString().setFormat(Format.BINARY));
                                    body.setRequired(true);
                                })
                                .response(204, resp -> {
                                    resp.setDescription("The file has been updated");
                                });
                        // TODO: add error response
                    })
                    .method(HttpMethod.DELETE, op -> {
                        op.response(204, resp -> {
                            resp.setDescription("The file has been deleted");
                        });
                        // TODO: add error response
                    })
                    .each(((method, op) -> {
                        op.eachResponse((status, resp) -> {
                            addResolved(context, method, op, semanticType);
                        });
                    }));
        });

        context.spec().getPaths().getItems().put("/"+entity.getPathSegment().getValue()+"/{id}/"+contentAttribute.getPathSegment().getValue(), contentPathItem);

    }

    private static void addRelation(OpenApiSpecContext context, Entity entity, Relation relation) {
        var isCollection = relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation;
        var entityName = relation.getTargetEndPoint().getEntity();

        var modifyMethod = isCollection?HttpMethod.POST:HttpMethod.PUT;

        var relationPath = "/"+entity.getPathSegment().getValue()+"/{id}/"+relation.getSourceEndPoint().getPathSegment().getValue();

        context.spec().getPaths().path(relationPath)
                .setParameters(List.of(ENTITY_ID_PARAM))
                .method(HttpMethod.GET, op -> {
                    op.response(200, resp -> {
                        resp.getContent().addJson(
                                isCollection?
                                        resolveCollectionSchema(entityName, context):
                                        resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON)
                        );
                    });
                    if(!isCollection) {
                        op.response(404, resp -> {});
                    }
                    // TODO: add error response
                })
                .method(modifyMethod, op -> {
                    op.requestBody(body -> {
                        body.setRequired(true);
                        BodyValue relationValue = new RelationBodyValue(relation.getTargetEndPoint()
                                .getEntity());
                        if(isCollection) {
                            relationValue = new ArrayBodyValue(relationValue);
                        }
                        body.getContent().addMediaType(MediaType.TEXT_URI_LIST, bodyValueToJsonSchema(context,
                                relationValue));
                    });
                    op.response(204, resp -> {});
                    // TODO: add error response
                })
                .method(HttpMethod.DELETE, op -> {
                    op.response(204, resp -> {});
                    // TODO: add error response
                });

        // For to-many relations, also have links to the individual items in the collection
        if (isCollection) {
            context.spec().getPaths().path(relationPath+"/{itemId}")
                    .setParameters(List.of(ENTITY_ID_PARAM, new OpenApiParameter("itemId", In.PATH)))
                    .method(HttpMethod.GET, op -> {
                        op.response(200, resp -> {
                            resp.getContent().addJson(resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON));
                        });
                        // TODO: add error response
                    })
                    .method(HttpMethod.DELETE, op -> {
                        op.response(204, resp -> {});
                        // TODO: add error response
                    });
        }
    }

    private static OpenApiPotentialReference<JsonSchema> resolveCollectionSchema(EntityName entityName, OpenApiSpecContext context) {
        return context.spec().getComponents().getSchemas().register(entityName.getValue()+"Collection", () -> {
            var collectionSchema = new JsonSchemaObject();
            collectionSchema.property("_embedded", new JsonSchemaObject()
                    .property("item", new JsonSchemaArray(resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON)))
            );
            collectionSchema.requiredProperty("page", createPage(context));
            return collectionSchema;
        });
    }

    private static OpenApiPotentialReference<JsonSchema> resolveItemSchema(EntityName entityName, OpenApiSpecContext context, BodyType bodyType, com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType mediaType) {

        var mimeTypeName = switch (mediaType) {
            case JSON -> "";
            case FLAT_JSON -> throw new IllegalArgumentException("FLAT_JSON is not supported");
            case FORM -> "Form";
            case MULTIPART_FORM -> "MultipartFormData";
        };

        var suffixName = switch (bodyType) {
            case RESPONSE -> "Response";
            case POST -> "Post"+mimeTypeName+"Body";
            case PUT -> "Put"+mimeTypeName+"Body";
            case PATCH -> "Patch"+mimeTypeName+"Body";
        };

        var schemaName = entityName.getValue() + suffixName;
        return context.spec().getComponents().getSchemas().register(schemaName, () -> {
            var body = BodyObjectMapper.forBody(new Context(context.application(), bodyType, mediaType, UserLocales.defaults()), entityName);
            var jsonSchema = (JsonSchemaObject) bodyValueToJsonSchema(context, body);
            if(bodyType == BodyType.RESPONSE) {
                jsonSchema
                        .requiredProperty("_links", new JsonSchemaObject().requiredProperty("self", createLink(context)));

            }

            return jsonSchema;
        });

    }

    private static OpenApiPotentialReference<JsonSchema> createPage(OpenApiSpecContext context) {
        return context.spec().getComponents().getSchemas().register("page", () -> new JsonSchemaObject()
                .requiredProperty("size", new JsonSchemaInteger()
                        .setDescription("Number of items shown on a single page"))
                .requiredProperty("total_items_estimate", new JsonSchemaInteger()
                        .setDescription("Estimated total number of items across all pages"))
                .requiredProperty("total_items_exact", new JsonSchemaOneOf(List.<OpenApiPotentialReference<JsonSchema>>of(new JsonSchemaInteger()
                                .setDescription("Exact total number of items across all pages (may be null if no exact number could be calculated"),
                        new JsonSchemaNull()
                )))
                .property("next_cursor", new JsonSchemaString()
                        .setDescription("Cursor to access the next page of results (absent if there is no next page)"))
                .property("prev_cursor", new JsonSchemaString()
                        .setDescription("Cursor to access the previous page of results (absent if there is no previous page)"))
                .setTitle("Page metadata")
        );
    }

    private static OpenApiPotentialReference<JsonSchema> createLink(OpenApiSpecContext context) {
        return context.spec().getComponents().getSchemas().register(
                "Link",
                () -> new JsonSchemaObject().requiredProperty("href", new JsonSchemaString().setFormat(Format.URI))
        );
    }

    private static JsonSchema bodyValueToJsonSchema(OpenApiSpecContext context, BodyValue bodyValue) {
        var jsonSchema = switch (bodyValue) {
            case ArrayBodyValue arrayBodyValue -> new JsonSchemaArray(bodyValueToJsonSchema(context, arrayBodyValue.getItems()));
            case ContentBodyValue contentBodyValue -> new JsonSchemaString().setFormat(Format.BINARY);
            case ObjectBodyValue objectBodyValue -> {
                var object = new JsonSchemaObject();
                for (var entry : objectBodyValue.getFields().entrySet()) {
                    var entryBodyValue = bodyValueToJsonSchema(context, entry.getValue());
                    if(entryBodyValue instanceof AbstractJsonSchemaDataType jsonSchemaDataType) {
                        // If the title is identical to the JSON key, leave it out as it provides no additional value
                        if (Objects.equals(jsonSchemaDataType.getTitle(), entry.getKey())) {
                            jsonSchemaDataType.setTitle(null);
                        }
                    }
                    object.property(entry.getKey(), entryBodyValue);
                    if(entry.getValue().isMandatory()) {
                        object.getRequired().add(entry.getKey());
                    }
                }
                yield object;
            }
            case RelationBodyValue relationBodyValue -> {
                var targetEntity = context.application().getRequiredEntityByName(relationBodyValue.getTargetEntity());

                yield new JsonSchemaString()
                        .setFormat(Format.URI)
                        .setExamples(List.of(
                                "https://contentgrid-app.example/%s/00000000-0000-0000-0000-000000000000".formatted(targetEntity.getPathSegment().getValue())
                        ));
            }
            case SimpleBodyValue simpleBodyValue -> {
                AbstractJsonSchemaDataType baseSchema = switch (simpleBodyValue.getType()) {
                    case LONG -> new JsonSchemaInteger();
                    case DOUBLE -> new JsonSchemaNumber();
                    case BOOLEAN -> new JsonSchemaBoolean();
                    case TEXT, UUID -> new JsonSchemaString();
                    case DATE -> new JsonSchemaString().setFormat(Format.DATE);
                    case DATETIME -> new JsonSchemaString().setFormat(Format.DATE_TIME);
                };
                var maybeAllowedValues = simpleBodyValue.getConstraint(AllowedValuesConstraint.class);
                if (maybeAllowedValues.isPresent()) {
                    baseSchema = new JsonSchemaEnum(maybeAllowedValues.get().getValues());
                }
                if (simpleBodyValue.isNullable()) {
                    yield new JsonSchemaOneOf(List.of(baseSchema, new JsonSchemaNull()));
                }
                yield baseSchema;
            }
        };

        if(jsonSchema instanceof AbstractJsonSchemaDataType schemaDataType) {
            schemaDataType
                    .setTitle(bodyValue.getTitle())
                    .setDescription(bodyValue.getDescription());
        }

        return jsonSchema;
    }

    private static void addResolved(OpenApiSpecContext context, HttpMethod method, OpenApiOperation operation, SemanticType semanticType) {
        operation.setParameters(PARAMETER_RESOLVER.resolveRequestParameters(new HttpRequestType(method, semanticType), context).toList())
                .eachResponse((statusCode, resp) -> {
                    RESPONSE_HEADER_RESOLVER.resolveResponseHeaders(new HttpResponseType(method, statusCode, semanticType), context)
                            .forEachOrdered(e -> resp.getHeaders().getItems().putIfAbsent(e.getKey(), e.getValue()));
                });
    }

}
