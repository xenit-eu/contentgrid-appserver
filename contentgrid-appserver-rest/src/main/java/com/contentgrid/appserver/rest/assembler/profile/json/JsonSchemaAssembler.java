package com.contentgrid.appserver.rest.assembler.profile.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RequiredConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.CompositeAttribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.UserAttribute;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.application.model.values.AttributePath;
import com.contentgrid.appserver.application.model.values.SimpleAttributePath;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.Definitions;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.EnumProperty;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.Item;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.JsonSchemaProperty;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;

public class JsonSchemaAssembler {

    public JsonSchema toModel(Application application, Entity entity) {
        var definitions = new Definitions();
        var title = readTitle(application, entity);
        var attributes = entity.getAllAttributes().stream()
                .map(attribute -> toProperty(application, entity, new SimpleAttributePath(attribute.getName()), attribute, definitions))
                .flatMap(Optional::stream);
        var relations = application.getRelationsForSourceEntity(entity).stream()
                .map(relation -> toProperty(application, entity, relation))
                .flatMap(Optional::stream);
        var properties = Stream.concat(attributes, relations).toList();

        return new JsonSchema(title == null ? entity.getName().getValue() : title, entity.getDescription(), properties, definitions);
    }

    private Optional<JsonSchemaProperty> toProperty(Application application, Entity entity, AttributePath path, Attribute attribute, Definitions definitions) {
        if (attribute.isIgnored()) {
            return Optional.empty();
        }

        var title = readTitle(application, entity, path);
        var required = isRequired(attribute);

        var property = new JsonSchemaProperty(attribute.getName().getValue(), title, attribute.getDescription(), required);

        if (attribute.isReadOnly()) {
            property.withReadOnly();
        }

        property = customizeProperty(property, application, entity, path, attribute, definitions);

        return Optional.of(property);
    }

    private static boolean isRequired(Attribute attribute) {
        return attribute instanceof SimpleAttribute simpleAttribute && simpleAttribute.hasConstraint(RequiredConstraint.class);
    }

    private JsonSchemaProperty customizeProperty(JsonSchemaProperty property, Application application, Entity entity, AttributePath path, Attribute attribute, Definitions definitions) {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> customizeSimpleProperty(property, simpleAttribute);
            case UserAttribute ignored -> customizeUserProperty(property);
            case ContentAttribute contentAttribute -> customizeContentProperty(property, application, entity, path, contentAttribute, definitions);
            case CompositeAttribute compositeAttribute -> customizeCompositeProperty(property, application, entity, path, compositeAttribute, definitions);
        };
    }

    private JsonSchemaProperty customizeSimpleProperty(JsonSchemaProperty property, SimpleAttribute simpleAttribute) {
        property.withType(toJsonSchemaType(simpleAttribute.getType()));
        var format = toJsonSchemaFormat(simpleAttribute.getType());
        if (format != null) {
            property.withFormat(format);
        }

        // Return enum property when there are allowed values
        if (simpleAttribute.hasConstraint(AllowedValuesConstraint.class)) {
            var values = new ArrayList<>(simpleAttribute.getConstraint(AllowedValuesConstraint.class)
                    .orElseThrow().getValues());

            if (!property.isRequired()) {
                // add null as enum value if not required
                values.add(null);
            }

            property = new EnumProperty(property.getName(), property.getTitle(), values, property.getDescription(),
                    property.isRequired());
        }
        return property;
    }

    private JsonSchemaProperty customizeUserProperty(JsonSchemaProperty property) {
        return property.withReadOnly().withType(JsonSchemaType.STRING);
    }

    private JsonSchemaProperty customizeContentProperty(JsonSchemaProperty property, Application application, Entity entity, AttributePath path, ContentAttribute contentAttribute, Definitions definitions) {
        // Customize JsonSchemaProperty
        var reference = JsonSchemaReference.named("content");
        property.withReference(reference);

        if (!definitions.hasDefinitionFor(reference)) {
            // Add content definition
            var properties = contentAttribute.getAttributes().stream()
                    .map(attribute -> toProperty(application, entity, path.withSuffix(attribute.getName()), attribute,
                            definitions))
                    .flatMap(Optional::stream)
                    .toList();

            definitions.addDefinition(reference, new Item(JsonSchemaType.OBJECT, properties));
        }

        return property;
    }

    private JsonSchemaProperty customizeCompositeProperty(JsonSchemaProperty property, Application application, Entity entity, AttributePath path, CompositeAttribute compositeAttribute, Definitions definitions) {
        var properties = compositeAttribute.getAttributes().stream()
                .map(attribute -> {
                    var newPath = path.withSuffix(attribute.getName());
                    return toProperty(application, entity, newPath, attribute, definitions);
                })
                .flatMap(Optional::stream)
                .toList();

        return property.withProperties(properties);
    }

    private JsonSchemaType toJsonSchemaType(SimpleAttribute.Type type) {
        return switch (type) {
            case TEXT, DATETIME, UUID -> JsonSchemaType.STRING;
            case LONG -> JsonSchemaType.INTEGER;
            case DOUBLE -> JsonSchemaType.NUMBER;
            case BOOLEAN -> JsonSchemaType.BOOLEAN;
        };
    }

    private JsonSchemaFormat toJsonSchemaFormat(SimpleAttribute.Type type) {
        return switch (type) {
            case DATETIME -> JsonSchemaFormat.DATE_TIME;
            case UUID -> JsonSchemaFormat.UUID;
            default -> null;
        };
    }

    private Optional<JsonSchemaProperty> toProperty(Application application, Entity entity, Relation relation) {
        var sourceEndPoint = relation.getSourceEndPoint();
        if (sourceEndPoint.hasFlag(HiddenEndpointFlag.class)) {
            return Optional.empty();
        }

        var title = readTitle(application, entity, relation);
        var manyTargetPerSource = relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation;
        var property = new JsonSchemaProperty(sourceEndPoint.getName().getValue(), title,
                sourceEndPoint.getDescription(), sourceEndPoint.isRequired());
        property.asAssociation(manyTargetPerSource);

        return Optional.of(property);
    }

    private String readTitle(Application application, Entity entity) {
        return null; // TODO: resolve entity title (ACC-2230)
    }

    private String readTitle(Application application, Entity entity, AttributePath path) {
        return null; // TODO: resolve attribute title (ACC-2230)
    }

    private String readTitle(Application application, Entity entity, Relation relation) {
        return null; // TODO: resolve relation title (ACC-2230)
    }

}
