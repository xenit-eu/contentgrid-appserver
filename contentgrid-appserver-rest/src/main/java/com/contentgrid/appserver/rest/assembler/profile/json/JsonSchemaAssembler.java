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
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.relations.ManyToManyRelation;
import com.contentgrid.appserver.application.model.relations.OneToManyRelation;
import com.contentgrid.appserver.application.model.relations.Relation;
import com.contentgrid.appserver.application.model.relations.flags.HiddenEndpointFlag;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.Definitions;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.EnumProperty;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.Item;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.JsonSchemaProperty;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Stream;

public class JsonSchemaAssembler {

    public record Context(Application application, UserLocales userLocales) {

    }

    public JsonSchema toModel(Entity entity, Context context) {
        var definitions = new Definitions();
        var translation = entity.getTranslations(context.userLocales());
        var attributes = entity.getAllAttributes().stream()
                .map(attribute -> toProperty(attribute, context, definitions))
                .flatMap(Optional::stream);
        var relations = context.application().getRelationsForSourceEntity(entity).stream()
                .map(relation -> toProperty(relation, context))
                .flatMap(Optional::stream);
        var properties = Stream.concat(attributes, relations).toList();

        return new JsonSchema(translation.getSingularName(), translation.getDescription(), properties, definitions);
    }

    private Optional<JsonSchemaProperty> toProperty(Attribute attribute, Context context, Definitions definitions) {
        if (attribute.isIgnored()) {
            return Optional.empty();
        }

        var translation = attribute.getTranslations(context.userLocales());

        var required = isRequired(attribute);

        var property = new JsonSchemaProperty(attribute.getName().getValue(), translation.getName(), translation.getDescription(), required);

        if (attribute.isReadOnly()) {
            property.withReadOnly();
        }

        property = customizeProperty(property, attribute, context, definitions);

        return Optional.of(property);
    }

    private static boolean isRequired(Attribute attribute) {
        return attribute instanceof SimpleAttribute simpleAttribute && simpleAttribute.hasConstraint(RequiredConstraint.class);
    }

    private JsonSchemaProperty customizeProperty(JsonSchemaProperty property, Attribute attribute, Context context, Definitions definitions) {
        return switch (attribute) {
            case SimpleAttribute simpleAttribute -> customizeSimpleProperty(property, simpleAttribute);
            case UserAttribute ignored -> customizeUserProperty(property);
            case ContentAttribute contentAttribute -> customizeContentProperty(property, contentAttribute, context, definitions);
            case CompositeAttribute compositeAttribute -> customizeCompositeProperty(property, compositeAttribute, context, definitions);
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

    private JsonSchemaProperty customizeContentProperty(JsonSchemaProperty property, ContentAttribute contentAttribute, Context context, Definitions definitions) {
        // Customize JsonSchemaProperty
        var reference = JsonSchemaReference.named("content");
        property.withReference(reference);

        if (!definitions.hasDefinitionFor(reference)) {
            // Add content definition
            var properties = contentAttribute.getAttributes().stream()
                    .map(attribute -> toProperty(attribute, context, definitions))
                    .flatMap(Optional::stream)
                    .toList();

            definitions.addDefinition(reference, new Item(JsonSchemaType.OBJECT, properties));
        }

        return property;
    }

    private JsonSchemaProperty customizeCompositeProperty(JsonSchemaProperty property, CompositeAttribute compositeAttribute, Context context, Definitions definitions) {
        var properties = compositeAttribute.getAttributes().stream()
                .map(attribute -> toProperty(attribute, context, definitions))
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

    private Optional<JsonSchemaProperty> toProperty(Relation relation, Context context) {
        var sourceEndPoint = relation.getSourceEndPoint();
        if (sourceEndPoint.hasFlag(HiddenEndpointFlag.class)) {
            return Optional.empty();
        }

        var translations = relation.getSourceEndPoint().getTranslations(context.userLocales());

        var property = new JsonSchemaProperty(
                sourceEndPoint.getName().getValue(),
                translations.getName(),
                translations.getDescription(),
                sourceEndPoint.isRequired()
        );

        if (relation instanceof OneToManyRelation || relation instanceof ManyToManyRelation) {
            property.asAssociationArray();
        } else {
            property.asAssociation();
        }

        return Optional.of(property);
    }

}
