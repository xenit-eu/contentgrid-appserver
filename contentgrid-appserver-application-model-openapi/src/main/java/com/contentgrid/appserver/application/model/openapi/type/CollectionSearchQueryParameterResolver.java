package com.contentgrid.appserver.application.model.openapi.type;

import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.openapi.OpenApiSpecContext;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter;
import com.contentgrid.appserver.application.model.openapi.model.OpenApiParameter.In;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaArray;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaEnum;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper.Context;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyType;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Resolver for the entity search filter query parameters
 */
@RequiredArgsConstructor
public class CollectionSearchQueryParameterResolver implements RequestParameterResolver {

    @NonNull
    private final BiFunction<BodyValue, OpenApiSpecContext, JsonSchema> bodyValueMapper;

    private boolean supports(HttpRequestType requestType) {
        return requestType.getType() instanceof CollectionType collectionType && collectionType.getElementType() instanceof EntityType;
    }

    @Override
    public Stream<OpenApiParameter> resolveRequestParameters(HttpRequestType requestType, OpenApiSpecContext context) {
        if(!supports(requestType)) {
            return Stream.empty();
        }
        var collectionType = (CollectionType) requestType.getType();
        var entityType = (EntityType) collectionType.getElementType();
        var entityName = entityType.getEntityName();
        var entity = context.application().getRequiredEntityByName(entityName);


        var sortSpecs = entity.getSortableFields()
                .stream()
                .flatMap(f -> {
                    var name = f.getName().getValue();
                    return Stream.of(
                            name+",asc",
                            name+",desc"
                    );
                })
                .toList();

        OpenApiParameter sortParam = null;
        if(!sortSpecs.isEmpty()) {
            // Sort parameter should only be present if there are sortable fields
            sortParam = new OpenApiParameter("_sort", In.QUERY)
                    .setSchema(new JsonSchemaArray(new JsonSchemaEnum(sortSpecs)));
        }

        var body = BodyObjectMapper.forSearch(context.application(), UserLocales.defaults(), entityName);

        return Stream.concat(
                body.getFields()
                        .entrySet()
                        .stream()
                .map(field -> {
                    var param = new OpenApiParameter(field.getKey(), In.QUERY);
                    param.setDescription(field.getValue().getDescription());
                    param.setSchema(bodyValueMapper.apply(
                            field.getValue()
                                    // Remove description, because it is already set on the param itself
                                    .withDescription(null)
                                    // Remove title, because it is the same as the param name
                                    .withTitle(null),
                            context
                    ));
                    return param;
                }),
                Stream.ofNullable(sortParam)
        );
    }

}
