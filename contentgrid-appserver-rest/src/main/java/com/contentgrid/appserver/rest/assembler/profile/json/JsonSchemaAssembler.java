package com.contentgrid.appserver.rest.assembler.profile.json;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Constraint.AllowedValuesConstraint;
import com.contentgrid.appserver.application.model.Constraint.RegexPatternConstraint;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ArrayBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyObjectMapper;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyType;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.BodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ContentBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.MediaType;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.ObjectBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.RelationBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SimpleBodyValue;
import com.contentgrid.appserver.application.model.openapi.model.rest.body.SourceType.AttributeSourceType;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.AbstractJsonSchemaProperty;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.Definitions;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.EnumProperty;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.Item;
import com.contentgrid.appserver.rest.assembler.profile.json.JsonSchema.JsonSchemaProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

public class JsonSchemaAssembler {

    public record Context(Application application, UserLocales userLocales) {

    }

    public JsonSchema toModel(Entity entity, Context context) {
        var postBody = BodyObjectMapper.forBody(
                new BodyObjectMapper.Context(context.application(), BodyType.POST, MediaType.JSON, context.userLocales()), entity.getName());
        var responseBody = BodyObjectMapper.forBody(
                new BodyObjectMapper.Context(context.application(), BodyType.RESPONSE, MediaType.JSON, context.userLocales()), entity.getName());

        var definitions = new Definitions();

        var properties = createProperties(context, new CreatePropertiesContext(postBody, responseBody), definitions).toList();

        return new JsonSchema(responseBody.getTitle(), responseBody.getDescription(), properties, definitions);
    }

    @RequiredArgsConstructor
    private static class CreatePropertiesContext {
        private final ObjectBodyValue postBody;
        private final ObjectBodyValue responseBody;

        public boolean isReadonly(String field) {
            return postBody.getField(field).isEmpty();
        }

        public boolean isRequired(String field) {
            return postBody.getField(field)
                    .map(BodyValue::isMandatory)
                    .orElse(false);
        }

        public Map<String, BodyValue> getFields() {
            var fields = LinkedHashMap.<String, BodyValue>newLinkedHashMap(postBody.getFields().size());
            fields.putAll(responseBody.getFields());
            fields.putAll(postBody.getFields());
            return fields;

        }

        public CreatePropertiesContext descend(String field) {
            return new CreatePropertiesContext(
                    postBody.getField(field).map(ObjectBodyValue.class::cast).orElse(new ObjectBodyValue(Collections.emptyMap())),
                    responseBody.getField(field).map(ObjectBodyValue.class::cast).orElse(new ObjectBodyValue(Collections.emptyMap()))
            );
        }
    }

    private Stream<JsonSchemaProperty> createProperties(Context context, CreatePropertiesContext createPropertiesContext, Definitions definitions) {
        return createPropertiesContext.getFields()
                .entrySet()
                .stream()
                .map(entry -> createProperty(context, entry, createPropertiesContext, definitions));
    }

    private JsonSchemaProperty createProperty(Context context, Entry<String, BodyValue> entry, CreatePropertiesContext createPropertiesContext, Definitions definitions) {
        var property = new JsonSchemaProperty(
                entry.getKey(),
                entry.getValue().getTitle(),
                entry.getValue().getDescription(),
                createPropertiesContext.isRequired(entry.getKey())
        );
        if (createPropertiesContext.isReadonly(entry.getKey())) {
            property.withReadOnly();
        }


        return switch (entry.getValue()) {
            case ArrayBodyValue arrayBodyValue -> {
                if (arrayBodyValue.getItems() instanceof RelationBodyValue) {
                    yield property.asAssociationArray();
                } else {
                    throw new IllegalArgumentException("Array value with non-relation body is not supported");
                }
            }
            case RelationBodyValue relationBodyValue -> property.asAssociation();
            case ContentBodyValue contentBodyValue -> throw new IllegalArgumentException("Content value is not supported");
            case ObjectBodyValue objectBodyValue -> {
                var properties = createProperties(context, createPropertiesContext.descend(entry.getKey()), definitions).toList();
                if(objectBodyValue.getSourceType() instanceof AttributeSourceType attributeSourceType) {
                    var isContentAttribute = context.application().getRequiredEntityByName(attributeSourceType.getEntityName())
                            .getNestedAttribute(attributeSourceType.getAttributePath())
                            .filter(ContentAttribute.class::isInstance)
                            .isPresent();
                    if (isContentAttribute) {
                        var ref = JsonSchemaReference.named("content");
                        definitions.addDefinition(ref, new Item(JsonSchemaType.OBJECT, properties));
                        yield property.withReference(ref);
                    }
                }
                if (properties.stream().allMatch(AbstractJsonSchemaProperty::isReadOnly)) {
                    property.withReadOnly();
                }
                yield property.withProperties(properties);
            }
            case SimpleBodyValue simpleBodyValue -> {
                var maybeAllowedValues = simpleBodyValue.getConstraint(AllowedValuesConstraint.class);
                if (maybeAllowedValues.isPresent()) {
                    property = new EnumProperty(property.getName(), property.getTitle(), maybeAllowedValues.get().getValues(), property.getDescription(), property.isRequired());
                }
                var maybePattern = simpleBodyValue.getConstraint(RegexPatternConstraint.class);
                if (maybePattern.isPresent()) {
                    property = property.withPattern(maybePattern.get().getHtmlPattern());
                }

                yield switch (simpleBodyValue.getType()) {
                    case LONG -> property.withType(JsonSchemaType.INTEGER);
                    case DOUBLE -> property.withType(JsonSchemaType.NUMBER);
                    case BOOLEAN -> property.withType(JsonSchemaType.BOOLEAN);
                    case TEXT -> property.withType(JsonSchemaType.STRING);
                    case DATE -> property.withType(JsonSchemaType.STRING).withFormat(JsonSchemaFormat.DATE);
                    case DATETIME -> property.withType(JsonSchemaType.STRING).withFormat(JsonSchemaFormat.DATE_TIME);
                    case UUID -> property.withType(JsonSchemaType.STRING).withFormat(JsonSchemaFormat.UUID);
                };
            }
        };
    }

}
