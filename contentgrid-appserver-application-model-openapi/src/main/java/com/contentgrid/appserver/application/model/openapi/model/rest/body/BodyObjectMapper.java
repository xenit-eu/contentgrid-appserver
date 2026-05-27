package com.contentgrid.appserver.application.model.openapi.model.rest.body;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Constraint.UniqueConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.attributes.flags.IgnoredFlag;
import com.contentgrid.appserver.application.model.attributes.flags.ReadOnlyFlag;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SourceType.EntitySourceType;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SourceType.RelationSourceType;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SourceType.SearchFilterSourceType;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.AttributeSearchFilter.Operation;
import com.contentgrid.appserver.application.model.searchfilters.BaseAttributeSearchFilter;
import com.contentgrid.appserver.application.model.searchfilters.flags.HiddenSearchFilterFlag;
import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.With;

/**
 * Maps an {@link Entity} to an {@link ObjectBodyValue} intermediate model for a given
 * {@link BodyType} and {@link MediaType}.
 * <p>
 * The resulting {@link ObjectBodyValue} can be used to derive OpenAPI request/response body schemas
 * or HAL-Forms properties without re-reading the application model.
 */
public final class BodyObjectMapper {

    @With(AccessLevel.PRIVATE)
    public record Context(
            Application application,
            BodyType bodyType,
            MediaType mediaType,
            UserLocales userLocales
    ) {

    }

    private BodyObjectMapper() {}

    /**
     * Maps an entity's search filters structure to an {@link ObjectBodyValue}
     * @param application the application for the mapping
     * @param userLocales the user's locales for translations
     * @param entityName      the entity to map
     */
    public static ObjectBodyValue forSearch(Application application, UserLocales userLocales, EntityName entityName) {
        var context = new Context(application, BodyType.RESPONSE, MediaType.FORM, userLocales);
        var entity = context.application().getRequiredEntityByName(entityName);
        var fields = new LinkedHashMap<String, BodyValue>();
        for (var searchFilter : entity.getSearchFilters()) {
            if (searchFilter.hasFlag(HiddenSearchFilterFlag.class)) {
                continue;
            }
            var translations = searchFilter.getTranslations(context.userLocales());
            var bodyValue = switch (searchFilter) {
                case BaseAttributeSearchFilter attributeSearchFilter -> {
                    var attribute = context.application().resolvePropertyPath(entity, attributeSearchFilter.getAttributePath());
                    yield getBodyValue(
                            context,
                            new SearchFilterSourceType(entityName, searchFilter.getName()),
                            attribute
                    );
                }
                default -> throw new IllegalStateException("Unexpected value: " + searchFilter);
            };

            if (
                    bodyValue instanceof SimpleBodyValue simpleBodyValue &&
                            !(searchFilter instanceof AttributeSearchFilter attributeSearchFilter &&
                            attributeSearchFilter.getOperation() == Operation.EXACT)
            ) {
                // Constraints don't apply to search filters; except to the 'exact' filter,
                // where the searched value must match a value exactly
                bodyValue = simpleBodyValue.toBuilder().clearConstraints().build();
            }


            if(bodyValue != null) {
                if(translations.getName() != null) {
                    bodyValue = bodyValue.withTitle(translations.getName());
                }

                if (translations.getDescription() != null) {
                    bodyValue = bodyValue.withDescription(translations.getDescription());
                }
                fields.put(
                        searchFilter.getName().getValue(),
                        bodyValue
                                // Search fields are never mandatory, and can't hold a null value
                                .withMandatory(false)
                                .withNullable(false)
                );
            }
        }
        return new ObjectBodyValue(Collections.unmodifiableMap(fields));

    }

    /**
     * Maps an entity's attribute structure to an {@link ObjectBodyValue}.
     *
     * @param context the context for the mapping
     * @param entityName      the entity to map
     */
    public static ObjectBodyValue forBody(Context context, EntityName entityName) {
        var entity = context.application().getRequiredEntityByName(entityName);
        var entityTranslations = entity.getTranslations(context.userLocales());

        var fields = new LinkedHashMap<String, BodyValue>();
        if (context.bodyType() == BodyType.RESPONSE) {
            // For responses, the primary key is also included
            mapAttribute(context, new EntitySourceType(entityName), entity.getPrimaryKey(), fields);
            // Update primary key field to mark it as not nullable (as it's always present in the response)
            fields.compute(entity.getPrimaryKey().getName().getValue(), (k, v) -> v.withMandatory(true).withNullable(false));
        }
        for (var attribute : entity.getAttributes()) {
            mapAttribute(context, new EntitySourceType(entityName), attribute, fields);
        }

        if (context.bodyType() == BodyType.POST) {
            // Only during creation, relations can also be set
            for (var relation : context.application().getRelationsForSourceEntity(entity)) {
                if (relation.getSourceEndPoint().hasFlag(HiddenEndpointFlag.class)) {
                    continue;
                }
                var sourceEndPoint = relation.getSourceEndPoint();
                var epTranslations = sourceEndPoint.getTranslations(context.userLocales());
                BodyValue relationValue = RelationBodyValue.builder()
                        .sourceType(new RelationSourceType(relation))
                        .targetEntity(relation.getTargetEndPoint().getEntity())
                        .build();

                // Multi-valued relations are an array
                if (relation instanceof OneToManyRelation
                        || relation instanceof ManyToManyRelation) {
                    // The array for to-many relations can't contain null values, and can not be null itself (it can be left out)
                    relationValue = new ArrayBodyValue(relationValue.withNullable(false)).withNullable(false);
                } else {
                    relationValue = relationValue
                            // For to-one relations that are required, they are required.
                            .withMandatory(sourceEndPoint.isRequired())
                            .withNullable(!sourceEndPoint.isRequired() && context.mediaType().canTransportNulls());
                }

                fields.put(sourceEndPoint.getName().getValue(),
                        relationValue
                                .withTitle(epTranslations.getName())
                                .withDescription(epTranslations.getDescription())
                );
            }
        }

        var result = ObjectBodyValue.builder()
                .sourceType(new EntitySourceType(entityName))
                .title(entityTranslations.getSingularName())
                .description(entityTranslations.getDescription())
                .mandatory(true)
                .nullable(false)
                .fields(Collections.unmodifiableMap(fields))
                .build();

        if(!context.mediaType().canTransportNestedObjects()) {
            // Non-JSON request bodies are flattened, because they don't support nested objects
            return flattened(result);
        }
        return result;
    }

    /**
     * Recursively flattens nested {@link ObjectBodyValue} entries into dot-notation keys,
     * concatenating titles with {@code ": "} as separator.
     * <p>
     * Mirrors the behaviour of {@code OpenApiEntityRequestBodyTypeResolver.flattened()}: a field
     * {@code "content"} (title {@code "Content"}) containing sub-fields {@code "filename"}
     * (title {@code "Filename"}) and {@code "mimetype"} (title {@code "Mimetype"}) becomes the
     * top-level keys {@code "content.filename"} (title {@code "Content: Filename"}) and
     * {@code "content.mimetype"} (title {@code "Content: Mimetype"}).
     * <p>
     * Nullability is propagated from parent to children: if the parent {@link ObjectBodyValue} is
     * nullable (i.e. the field is optional), all its flattened children also become nullable,
     * because omitting the parent implicitly omits every child.
     */
    private static ObjectBodyValue flattened(ObjectBodyValue object) {
        return flattened(object, null);
    }

    private static ObjectBodyValue flattened(ObjectBodyValue object, String titlePrefix) {
        var flatFields = new LinkedHashMap<String, BodyValue>();
        for (var entry : object.getFields().entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            if (value instanceof ObjectBodyValue nestedObject) {
                var nestedTitlePrefix = concatTitles(titlePrefix, nestedObject.getTitle());
                var flatNested = flattened(nestedObject, nestedTitlePrefix);
                for (var nestedEntry : flatNested.getFields().entrySet()) {
                    var nestedValue = nestedEntry.getValue();
                    if (!nestedObject.isMandatory()) {
                        nestedValue = nestedValue.withMandatory(false);
                    }
                    flatFields.put(key + "." + nestedEntry.getKey(), nestedValue);
                }
            } else {
                flatFields.put(key, value.withTitle(concatTitles(titlePrefix, value.getTitle())));
            }
        }
        return object.toBuilder()
                .clearFields()
                .fields(Collections.unmodifiableMap(flatFields))
                .build();
    }

    private static String concatTitles(String a, String b) {
        if (a == null || a.isEmpty()) {
            return b;
        }
        if (b == null || b.isEmpty()) {
            return a;
        }
        return a + ": " + b;
    }

    /**
     * Converts a single {@link Attribute} to a {@link BodyValue} and adds it to {@code fields}.
     * Ignored attributes and {@link UserAttribute}s are silently skipped.
     */
    private static void mapAttribute(Context context, SourceType sourceType, Attribute attribute, Map<String, BodyValue> fields) {
        if (attribute.hasFlag(IgnoredFlag.class)) {
            return;
        }
        if (context.bodyType() != BodyType.RESPONSE && attribute.hasFlag(ReadOnlyFlag.class)) {
            return;
        }


        var value = getBodyValue(
                context,
                sourceType.nested(attribute.getName()),
                attribute
        );

        if (value != null) {
            fields.put(attribute.getName().getValue(), value);
        }
    }

    private static BodyValue getBodyValue(Context context, SourceType sourceType, Attribute attribute) {
        var bodyValue = switch (attribute) {
            case SimpleAttribute sa -> SimpleBodyValue.builder()
                    .sourceType(sourceType)
                    .nullable(!sa.hasConstraint(RequiredConstraint.class))
                    .type(sa.getType())
                    .constraints(sa.getConstraints().stream()
                            // Filter out required & unique constraints.
                            // required is already reflected in 'nullable', and unique has nothing to do with body structure
                            .filter(c -> !(c instanceof RequiredConstraint || c instanceof UniqueConstraint))
                            .toList()
                    )
                    .build();
            case ContentAttribute ca -> {
                if (context.mediaType().canTransportContent()) {
                    // For multipart forms, use a special type for content upload
                    yield ContentBodyValue.builder()
                            .sourceType(sourceType)
                            .nullable(true)
                            .build();
                }
                if (context.bodyType() == BodyType.POST) {
                    // For non-multipart POST forms, the content never exists, so filename/mimetype can't be specified
                    yield null;
                }
                // For JSON / FORM media types, treat content as an object so that
                // sub-attributes (filename, mimetype) appear as regular fields
                Map<String, BodyValue> fields = new LinkedHashMap<>();

                fields.put("filename",
                        getBodyValue(context, sourceType.nested(ca.getFilename().getName()), ca.getFilename()));
                // If the content object is present, the mimetype must always be set as well
                fields.put("mimetype", getBodyValue(context, sourceType.nested(ca.getMimetype().getName()), ca.getMimetype())
                        .withNullable(false)
                        // Mandatory, except when using PATCH, as everything is optional when using PATCH
                        .withMandatory(context.bodyType() != BodyType.PATCH)
                );

                if (context.bodyType() == BodyType.RESPONSE) {
                    // Length is only sent in responses, it can't be set in requests
                    fields.put("length", getBodyValue(context, sourceType.nested(ca.getLength().getName()), ca.getLength()).withNullable(false).withMandatory(true));
                }

                yield ObjectBodyValue.builder()
                        .sourceType(sourceType)
                        .fields(fields)
                        // Content objects are always nullable, they can be absent when reading, or can be set to null to clear them
                        .nullable(true)
                        .build();
            }
            case UserAttribute ua -> {
                if(context.bodyType() == BodyType.RESPONSE) {
                    // For responses, this just is the user's name
                    yield getBodyValue(context, sourceType.nested(ua.getUsername().getName()), ua.getUsername());
                }

                // For requests, it is not defined yet how to handle, so skip it
                yield null;
            }
            case CompositeAttribute ca -> {
                var nestedFields = new LinkedHashMap<String, BodyValue>();
                for (var nestedAttr : ca.getAttributes()) {
                    mapAttribute(context, sourceType, nestedAttr, nestedFields);
                }
                yield ObjectBodyValue.builder()
                        .fields(Collections.unmodifiableMap(nestedFields))
                        .sourceType(sourceType)
                        .build();
            }
        };

        if (bodyValue != null) {
            bodyValue = switch (context.bodyType()) {
                case RESPONSE -> bodyValue.withMandatory(true); // All items are always present in the response
                case PUT, POST -> bodyValue.isNullable() ? bodyValue : bodyValue.withMandatory(
                        true); // All non-nullable values are required when POST or PUT (keys that are left out are set to null)
                case PATCH -> bodyValue.withMandatory(false); // No items are mandatory for PATCH (keys that are left out are kept as-is)
            };

            if(!context.mediaType().canTransportNulls()) {
                bodyValue = bodyValue.withNullable(false); // Form fields can't convey 'null', they can only leave the field out
            }

            var translations = attribute.getTranslations(context.userLocales());
            if (translations.getName() != null && !translations.getName().isEmpty()) {
                bodyValue = bodyValue.withTitle(translations.getName());
            }
            if (translations.getDescription() != null && !translations.getDescription().isEmpty()) {
                bodyValue = bodyValue.withDescription(translations.getDescription());
            }
        }

        return bodyValue;
    }
}
