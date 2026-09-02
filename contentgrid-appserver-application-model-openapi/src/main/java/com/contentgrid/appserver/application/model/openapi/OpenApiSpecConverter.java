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
import com.contentgrid.appserver.application.model.openapi.model.OpenApiReference;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiSpec;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiTag;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.AbstractJsonSchemaDataType;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaArray;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaBoolean;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaInteger;
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
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SourceType.AttributeSourceType;
import com.contentgrid.appserver.application.model.openapi.resolver.CollectionPaginationQueryParameterResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.CollectionSearchQueryParameterResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.CompositeParameterResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.ContentDispositionHeadersResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.ProblemsResponseResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.RequestParameterResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.ResponseHeaderResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.ResponseResolver;
import com.contentgrid.appserver.application.model.openapi.resolver.VersioningHeadersResolver;
import com.contentgrid.appserver.application.model.openapi.type.AttributeType;
import com.contentgrid.appserver.application.model.openapi.type.CollectionType;
import com.contentgrid.appserver.application.model.openapi.type.EntityType;
import com.contentgrid.appserver.application.model.openapi.type.HttpRequestType;
import com.contentgrid.appserver.application.model.openapi.type.HttpResponseType;
import com.contentgrid.appserver.application.model.openapi.type.RelationItemType;
import com.contentgrid.appserver.application.model.openapi.type.RelationType;
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
import lombok.experimental.UtilityClass;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

@UtilityClass
public class OpenApiSpecConverter {

    private static final RequestParameterResolver PARAMETER_RESOLVER;
    private static final ResponseHeaderResolver RESPONSE_HEADER_RESOLVER;
    private static final ResponseResolver RESPONSE_RESOLVER;

    public static final OpenApiParameter ENTITY_ID_PARAM = new OpenApiParameter("id", In.PATH)
            .setRequired(true)
            .setSchema(new JsonSchemaString());

    static {
        List<Object> resolvers = new ArrayList<>();
        var compositeResolver = new CompositeParameterResolver(resolvers);
        PARAMETER_RESOLVER = compositeResolver;
        RESPONSE_HEADER_RESOLVER = compositeResolver;
        RESPONSE_RESOLVER = compositeResolver;

        resolvers.add(new CollectionSearchQueryParameterResolver((bv, c) -> bodyValueToJsonSchema(c, bv)));
        resolvers.add(new CollectionPaginationQueryParameterResolver());
        resolvers.add(new VersioningHeadersResolver());
        resolvers.add(new ProblemsResponseResolver());
        resolvers.add(new ContentDispositionHeadersResolver());
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
                    op.setOperationId("list."+entityName.getValue());
                    var collectionType = new CollectionType(semanticType);
                    op.tag(tag.getName())
                            .setSummary("Retrieve %s list".formatted(entityName.getValue()))
                            .response(200, resp -> {
                                resp.setDescription("OK");
                                resp.getContent().addJson(resolveCollectionSchema(entityName, context));
                            });
                    addResolved(context, HttpMethod.GET, op, collectionType);
                })
                .method(HttpMethod.POST, op -> {
                    op
                            .setOperationId("create."+entityName.getValue())
                            .setSummary("Create a new %s".formatted(entityName.getValue()))
                            .tag(tag.getName())
                            .requestBody(body -> {
                                body.getContent().addMediaType(MediaType.APPLICATION_JSON, resolveItemSchema(entityName, context, BodyType.POST, JSON));
                                body.getContent().addMediaType(MediaType.APPLICATION_X_WWW_FORM_URLENCODED, resolveItemSchema(entityName, context, BodyType.POST, FORM));
                                body.getContent().addMediaType(MediaType.MULTIPART_FORM_DATA, resolveItemSchema(entityName, context, BodyType.POST, MULTIPART_FORM));
                                body.setRequired(true);
                            })
                            .response(201, resp -> {
                                resp.setDescription("The %s has been created".formatted(entityName.getValue()));
                                resp.getHeaders().header("Location", h -> h
                                        .setDescription("The URL of the created %s".formatted(entityName.getValue()))
                                        .setRequired(true)
                                        .setSchema(new JsonSchemaString().setFormat(Format.URI)
                                                .setExamples(List.of(
                                                        "https://contentgrid-app.example/%s/00000000-0000-0000-0000-000000000000".formatted(entity.getPathSegment().getValue())
                                                ))
                                        )
                                );
                                resp.getContent().addJson(resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON));
                            })
                            .response(400, resp -> {
                                resp.setDescription("The %s can not be created due to a problem with the submitted data".formatted(entityName.getValue()));
                            })
                    ;

                    addResolved(context, HttpMethod.POST, op, semanticType);
                })
                .combineParameters();

        // item
        context.spec().getPaths().path("/"+entity.getPathSegment().getValue()+"/{id}")
                .parameter(ENTITY_ID_PARAM)
                .method(HttpMethod.GET, op -> {
                    op.setOperationId("get."+entityName.getValue());
                    op.setSummary("Retrieve the %s".formatted(entityName.getValue()));
                    op.response(200, resp -> {
                        resp.setDescription("OK");
                        resp.getContent().addJson(resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON));
                    });
                })
                .method(HttpMethod.PUT, op -> {
                    op.setOperationId("update."+entityName.getValue());
                    op.setSummary("Update all attributes of the %s".formatted(entityName.getValue()));
                    op.requestBody(body -> {
                                body.setDescription("All attributes of the %s have to be specified. Missing attributes are treated as null".formatted(entityName.getValue()));
                                body.getContent().addJson(resolveItemSchema(entityName, context, BodyType.PUT, JSON));
                                body.getContent().addMediaType(MediaType.APPLICATION_X_WWW_FORM_URLENCODED, resolveItemSchema(entityName, context, BodyType.PUT, FORM));
                                body.setRequired(true);
                            })
                            .response(204, resp -> {
                                resp.setDescription("The %s has been updated".formatted(entityName.getValue()));
                            })
                            .response(400, resp -> {
                                resp.setDescription("The %s can not be updated due to a problem with the submitted data".formatted(entityName.getValue()));
                            });
                })
                .method(HttpMethod.PATCH, op -> {
                    op.setOperationId("patch."+entityName.getValue());
                    op.setSummary("Update some attributes of the %s".formatted(entityName.getValue()));
                    op.requestBody(body -> {
                                body.setDescription("Only attributes that have to be updated should be specified, other attributes should not be present");
                                body.getContent().addJson(resolveItemSchema(entityName, context, BodyType.PATCH, JSON));
                                body.getContent().addMediaType(MediaType.APPLICATION_X_WWW_FORM_URLENCODED, resolveItemSchema(entityName, context, BodyType.PATCH, FORM));
                                body.setRequired(true);
                            })
                            .response(204, resp -> {
                                resp.setDescription("The %s has been updated".formatted(entityName.getValue()));
                            })
                            .response(400, resp -> {
                                resp.setDescription("The %s can not be updated due to a problem with the submitted data".formatted(entityName.getValue()));
                            });
                })
                .method(HttpMethod.DELETE, op -> {
                    op.setOperationId("delete."+entityName.getValue());
                    op.setSummary("Delete the %s".formatted(entityName.getValue()));
                    op.response(204, resp -> {
                        resp.setDescription("The %s has been deleted".formatted(entityName.getValue()));
                    });
                    op.response(409, resp -> {
                        resp.setDescription("The %s can not be deleted because it is linked via a required relation".formatted(entityName.getValue()));
                    });
                })
                .each(((method, openApiOperation) -> {
                    openApiOperation.tag(tag.getName());
                    addResolved(context, method, openApiOperation, semanticType);
                }))
                .combineParameters();


        for (var relation : context.application().getRelationsForSourceEntity(entity)) {
            if(relation.getSourceEndPoint().hasFlag(HiddenEndpointFlag.class)) {
                continue;
            }

            addRelation(context, entity, relation);
        }

        for (var contentAttribute : entity.getContentAttributes()) {
            if(contentAttribute.hasFlag(IgnoredFlag.class)) {
                continue;
            }

            addContentAttribute(context, entity, contentAttribute);
        }
    }

    private static void addContentAttribute(OpenApiSpecContext context, Entity entity,
            ContentAttribute contentAttribute) {
        var semanticType = AttributeType.of(contentAttribute);
        var contentPathItem = new OpenApiPathItem()
                .parameter(ENTITY_ID_PARAM)
                .method(HttpMethod.GET, op -> {
                    op.setOperationId("get."+entity.getName().getValue()+"."+contentAttribute.getName());
                    op.setSummary("Retrieve the %s file stored with %s".formatted(
                            contentAttribute.getName().getValue(),
                            entity.getName().getValue()
                    ));
                    op.response(200, resp -> {
                        resp.setDescription("Contents of the stored file");
                        resp.getContent().addMediaType("*/*", new JsonSchemaString().setFormat(Format.BINARY));
                    });
                    op.response(404, resp -> {
                        resp.setDescription("No %s file is stored with %s".formatted(contentAttribute.getName().getValue(), entity.getName().getValue()));
                    });
                })
                .method(HttpMethod.PUT, op -> {
                    op.setOperationId("set."+entity.getName().getValue()+"."+contentAttribute.getName());
                    op
                            .setSummary("Add or update the %s file stored with %s".formatted(
                                    contentAttribute.getName().getValue(),
                                    entity.getName().getValue()
                            ))
                            .requestBody(body -> {
                                body.setDescription("File data to store");
                                body.getContent().addMediaType("*/*", new JsonSchemaString().setFormat(Format.BINARY));
                                body.setRequired(true);
                            })
                            .response(204, resp -> {
                                resp.setDescription("The file is uploaded");
                            });
                })
                .method(HttpMethod.DELETE, op -> {
                    op.setOperationId("delete."+entity.getName().getValue()+"."+contentAttribute.getName());
                    op.setSummary("Delete the %s file stored with %s".formatted(
                            contentAttribute.getName().getValue(),
                            entity.getName().getValue()
                    ));
                    op.response(204, resp -> {
                        resp.setDescription("The file has been deleted");
                    });
                })
                .each(((method, op) -> {
                    op.tag(entity.getName().getValue());
                    addResolved(context, method, op, semanticType);
                }))
                .combineParameters();

        context.spec().getPaths().getItems().put("/"+entity.getPathSegment().getValue()+"/{id}/"+contentAttribute.getPathSegment().getValue(), contentPathItem);

    }

    private static void addRelation(OpenApiSpecContext context, Entity entity, Relation relation) {
        var isCollection = relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation;
        var entityName = relation.getTargetEndPoint().getEntity();

        var modifyMethod = isCollection?HttpMethod.POST:HttpMethod.PUT;

        SemanticType targetType = new EntityType(entityName);
        if(isCollection) {
            targetType = new CollectionType(targetType);
        }
        var semanticType = new RelationType(targetType);

        context.spec().getPaths().path("/"+entity.getPathSegment().getValue()+"/{id}/"+relation.getSourceEndPoint().getPathSegment().getValue())
                .parameter(ENTITY_ID_PARAM)
                .method(HttpMethod.GET, op -> {
                    op.setOperationId("get."+entity.getName().getValue()+"."+relation.getSourceEndPoint().getName().getValue());
                    if(isCollection) {
                        op.setSummary("Retrieve the %s list linked with %s as %s".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    } else {
                        op.setSummary("Retrieve the %s linked with %s as %s".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    }
                    op.response(200, resp -> {
                        resp.setDescription("OK");
                        resp.getContent().addJson(
                                isCollection?
                                        resolveCollectionSchema(entityName, context):
                                        resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON)
                        );
                    });
                    if(!isCollection) {
                        op.response(404, resp -> {
                            resp.setDescription("The %s relation does not link to any %s".formatted(relation.getSourceEndPoint().getName().getValue(), relation.getTargetEndPoint().getEntity().getValue()));
                        });
                    }
                })
                .method(modifyMethod, op -> {
                    if(modifyMethod == HttpMethod.PUT) {
                        op.setOperationId("set."+entity.getName().getValue()+"."+relation.getSourceEndPoint().getName().getValue());
                        op.setSummary("Set the %s that is linked with %s as %s".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    } else {
                        op.setOperationId("add."+entity.getName().getValue()+"."+relation.getSourceEndPoint().getName().getValue());
                        op.setSummary("Add links to %s list that is linked with %s as %s, in addition to the existing %1$s list".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    }
                    op.requestBody(body -> {

                        body.setRequired(true);
                        BodyValue relationValue = new RelationBodyValue(relation.getTargetEndPoint()
                                .getEntity());
                        if(isCollection) {
                            relationValue = ArrayBodyValue.builder().items(relationValue).uniqueItems(true).build();
                            body.setDescription("Newline separated list of %s URIs".formatted(relation.getTargetEndPoint().getEntity().getValue()));
                        } else {
                            body.setDescription("One %s URI".formatted(relation.getTargetEndPoint().getEntity().getValue()));
                        }
                        body.getContent().addMediaType(MediaType.TEXT_URI_LIST, bodyValueToJsonSchema(context,
                                relationValue));
                    });
                    op.response(204, resp -> {
                        if(isCollection) {
                            resp.setDescription("The list of %s is linked with %s as %s".formatted(
                                    relation.getTargetEndPoint().getEntity().getValue(),
                                    relation.getSourceEndPoint().getEntity().getValue(),
                                    relation.getSourceEndPoint().getName().getValue()
                            ));
                        } else {
                            resp.setDescription("The %s is linked with %s as %s".formatted(
                                    relation.getTargetEndPoint().getEntity().getValue(),
                                    relation.getSourceEndPoint().getEntity().getValue(),
                                    relation.getSourceEndPoint().getName().getValue()
                            ));
                        }
                    });
                    if(!isCollection) {
                        op.response(400, resp -> {
                            resp.setDescription("Multiple URIs are given, but %s can only refer to one item".formatted(relation.getSourceEndPoint().getName().getValue()));
                        });
                    }
                    op.response(409, resp -> {
                        resp.setDescription("The URI list contains some %s that is already linked with a different %s as %s".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    });
                })
                .method(HttpMethod.DELETE, op -> {
                    op.setOperationId("clear."+entity.getName().getValue()+"."+relation.getSourceEndPoint().getName().getValue());
                    if(isCollection) {
                        op.setSummary("Removes all links to %s from %s".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    } else {
                        op.setSummary("Removes the link to %s from %s".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    }
                    op.response(204, resp -> {
                        if(isCollection) {
                            resp.setDescription("All links to %s have been removed from %s".formatted(
                                    relation.getTargetEndPoint().getEntity().getValue(),
                                    relation.getSourceEndPoint().getName().getValue()
                            ));
                        } else {
                            resp.setDescription("The link to %s has been removed from %s".formatted(
                                    relation.getTargetEndPoint().getEntity().getValue(),
                                    relation.getSourceEndPoint().getName().getValue()
                            ));
                        }
                    });
                    if(!isCollection) {
                        op.response(400, resp -> {
                            resp.setDescription("You can not remove the %s link, because it is marked as required".formatted(
                                    relation.getSourceEndPoint().getName().getValue()
                            ));
                        });
                    }
                    op.response(409, resp -> {
                        resp.setDescription("You can not remove the %s link, because the inverse relation is marked as required".formatted(
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    });
                })
                .each(((method, op) -> {
                    op.tag(relation.getSourceEndPoint().getEntity().getValue());
                    addResolved(context, method, op, semanticType);
                }))
                .combineParameters();

        if (isCollection) {
            // For to-many relations, also have links to the individual items in the collection
            addRelationItem(context, entity, relation);
        }
    }

    private static void addRelationItem(OpenApiSpecContext context, Entity entity, Relation relation) {
        var entityName = relation.getTargetEndPoint().getEntity();
        context.spec().getPaths().path("/"+entity.getPathSegment().getValue()+"/{id}/"+relation.getSourceEndPoint().getPathSegment().getValue()+"/{itemId}")
                .parameter(ENTITY_ID_PARAM)
                .parameter(new OpenApiParameter("itemId", In.PATH).setRequired(true).setSchema(new JsonSchemaString()))
                .method(HttpMethod.GET, op -> {
                    op.setOperationId("get."+ entity.getName().getValue()+"."+ relation.getSourceEndPoint().getName().getValue()+".item");
                    op.setSummary("Retrieve the %s identified by 'itemId' linked with %s as %s".formatted(
                            relation.getTargetEndPoint().getEntity().getValue(),
                            relation.getSourceEndPoint().getEntity().getValue(),
                            relation.getSourceEndPoint().getName().getValue()
                    ));
                    op.response(200, resp -> {
                        resp.setDescription("OK");
                        resp.getContent().addJson(resolveItemSchema(entityName, context, BodyType.RESPONSE, JSON));
                    });
                    op.response(404, resp -> {
                        resp.setDescription("The %s relation does not link to the %s identified by 'itemId'".formatted(
                                relation.getSourceEndPoint().getName().getValue(),
                                relation.getTargetEndPoint().getEntity().getValue()
                        ));
                    });
                })
                .method(HttpMethod.DELETE, op -> {
                    op.setOperationId("delete."+ entity.getName().getValue()+"."+ relation.getSourceEndPoint().getName().getValue()+".item");
                    op.setSummary("Removes the link to %s identified by 'itemId' from %s".formatted(
                            relation.getTargetEndPoint().getEntity().getValue(),
                            relation.getSourceEndPoint().getName().getValue()
                    ));
                    op.response(204, resp -> {
                        resp.setDescription("The link to %s has been removed from %s".formatted(
                                relation.getTargetEndPoint().getEntity().getValue(),
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    });
                    op.response(404, resp -> {
                        resp.setDescription("The %s relation does not link to the %s identified by 'itemId'".formatted(
                                relation.getSourceEndPoint().getName().getValue(),
                                relation.getTargetEndPoint().getEntity().getValue()
                        ));
                    });
                    op.response(409, resp -> {
                        resp.setDescription("You can not remove the %s link, because the inverse relation is marked as required".formatted(
                                relation.getSourceEndPoint().getName().getValue()
                        ));
                    });
                })
                .each(((method, op) -> {
                    op.tag(relation.getSourceEndPoint().getEntity().getValue());
                    addResolved(context, method, op, new RelationItemType(relation.getSourceEndPoint().getEntity()));
                }))
                .combineParameters();
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
            var jsonSchema = (JsonSchemaObject) bodyValueToJsonSchema(context, body, bodyType);
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
                .property("total_items_exact", new JsonSchemaInteger()
                                .setDescription("Exact total number of items across all pages (may be absent if no exact number could be calculated)")
                )
                .property("next_cursor", new JsonSchemaString()
                        .setDescription("Cursor to access the next page of results (absent if there is no next page)")
                        .setExamples(List.of("0msa4pz0"))
                )
                .property("prev_cursor", new JsonSchemaString()
                        .setDescription("Cursor to access the previous page of results (absent if there is no previous page)")
                        .setExamples(List.of("1mlpulv1"))
                )
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
        return (JsonSchema) bodyValueToJsonSchema(context, bodyValue, null);
    }

    private static OpenApiPotentialReference<JsonSchema> bodyValueToJsonSchema(OpenApiSpecContext context, BodyValue bodyValue, BodyType bodyType) {
        AbstractJsonSchemaDataType jsonSchema = switch (bodyValue) {
            case ArrayBodyValue arrayBodyValue -> {
                var array = new JsonSchemaArray(bodyValueToJsonSchema(context, arrayBodyValue.getItems(), bodyType));
                if (arrayBodyValue.isUniqueItems()) {
                    array.setUniqueItems(true);
                }
                yield array;
            }
            case ContentBodyValue contentBodyValue -> new JsonSchemaString().setFormat(Format.BINARY);
            case ObjectBodyValue objectBodyValue -> {
                var object = new JsonSchemaObject();
                for (var entry : objectBodyValue.getFields().entrySet()) {
                    var entryBodyValue = bodyValueToJsonSchema(context, entry.getValue(), bodyType);
                    removeBodyValueTitleIfEqualToKey(entry.getKey(), entryBodyValue);
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
                    case LONG -> new JsonSchemaInteger().setFormat(JsonSchemaNumber.Format.INT64);
                    case DOUBLE -> new JsonSchemaNumber().setFormat(JsonSchemaNumber.Format.DOUBLE);
                    case BOOLEAN -> new JsonSchemaBoolean();
                    case TEXT -> new JsonSchemaString();
                    case UUID -> new JsonSchemaString().setFormat(Format.UUID);
                    case DATE -> new JsonSchemaString().setFormat(Format.DATE);
                    case DATETIME -> new JsonSchemaString().setFormat(Format.DATE_TIME);
                };
                var maybeAllowedValues = simpleBodyValue.getConstraint(AllowedValuesConstraint.class);
                if (maybeAllowedValues.isPresent()) {
                    baseSchema = new JsonSchemaEnum(maybeAllowedValues.get().getValues());
                }
                yield baseSchema;
            }
        };

        if(bodyValue.isNullable()) {
            jsonSchema = jsonSchema.orNull();
        }

        if(bodyType != null && jsonSchema instanceof JsonSchemaObject jsonSchemaObject && bodyValue.getSourceType() instanceof AttributeSourceType attributeSourceType) {
            var entity = context.application().getRequiredEntityByName(attributeSourceType.getEntityName());
            var attribute = entity.getNestedAttribute(attributeSourceType.getAttributePath()).orElseThrow();
            if (attribute instanceof ContentAttribute) {
                var name = switch (bodyType) {
                    case RESPONSE -> "ContentInfo";
                    case POST -> "ContentInfoPOST";
                    case PUT -> "ContentInfoPUT";
                    case PATCH ->  "ContentInfoPATCH";
                };
                return context.spec().getComponents().getSchemas().register(name, jsonSchemaObject);
            }
        }

        jsonSchema
                .setTitle(bodyValue.getTitle())
                .setDescription(bodyValue.getDescription());

        return jsonSchema;
    }

    private static void removeBodyValueTitleIfEqualToKey(String key, OpenApiPotentialReference<JsonSchema> entryBodyValue) {
        switch (entryBodyValue) {
            case AbstractJsonSchemaDataType abstractJsonSchemaDataType -> {
                // If the title is identical to the JSON key, leave it out as it provides no additional value
                if (Objects.equals(abstractJsonSchemaDataType.getTitle(), key)) {
                    abstractJsonSchemaDataType.setTitle(null);
                }
            }
            case JsonSchemaOneOf jsonSchemaOneOf -> {
                jsonSchemaOneOf.getOneOf().forEach(item -> removeBodyValueTitleIfEqualToKey(key, item));
            }
            case OpenApiReference<?> reference -> {
                // This is a reference; there is nothing to do here
            }
            default -> throw new IllegalStateException("Unexpected value: " + entryBodyValue);
        }
    }

    private static void addResolved(OpenApiSpecContext context, HttpMethod method, OpenApiOperation operation, SemanticType semanticType) {
        RESPONSE_RESOLVER.resolveResponse(new HttpRequestType(method, semanticType), context)
                .forEachOrdered(entry ->
                        operation.getResponses().compute(entry.getKey(), (k, existing) -> {
                            if(existing == null) {
                                return entry.getValue();
                            } else {
                                var existingDescription = existing.getOriginalObject().getDescription();
                                var existingSummary = existing.getOriginalObject().getSummary();
                                var updated = existing.getOriginalObject().combinedWith(entry.getValue().getOriginalObject());
                                // Restore existing description & summary, because they are more tailored to the situation than the generic generated description
                                if (existingDescription != null) {
                                    updated.setDescription(existingDescription);
                                }
                                if (existingSummary != null) {
                                    updated.setSummary(existingSummary);
                                }
                                return updated;
                            }
                        })
                );
        operation.parameters(PARAMETER_RESOLVER.resolveRequestParameters(new HttpRequestType(method, semanticType), context).toList())
                .eachResponse((statusCode, resp) -> {
                    RESPONSE_HEADER_RESOLVER.resolveResponseHeaders(new HttpResponseType(method, statusCode, semanticType), context)
                            .forEachOrdered(e -> resp.getHeaders().getItems().putIfAbsent(e.getKey(), e.getValue()));
                });
    }

    public static final class Writer {

        private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();

        private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();

        public static String toJson(OpenApiSpec spec) {
            return JSON_MAPPER.writeValueAsString(spec);
        }

        public static String toYaml(OpenApiSpec spec) {
            return YAML_MAPPER.writeValueAsString(spec);
        }
    }
}
