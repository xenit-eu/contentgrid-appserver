package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaComposite.Serializer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@JsonSerialize(using = Serializer.class)
public final class JsonSchemaComposite implements JsonSchema {
    @Getter
    private final List<JsonSchema> schemas;

    public JsonSchemaComposite(JsonSchema... schemas) {
        this(List.of(schemas));
    }

    @Override
    public <T extends JsonSchema> Optional<T> ofType(Class<T> type) {
        return JsonSchema.super.ofType(type).or(() -> schemas.stream()
                .filter(type::isInstance)
                .map(type::cast)
                // Obtains the last type; matching how the serializer implementation decides which one to return
                .reduce((a, b) -> b));
    }

    static class Serializer extends ValueSerializer<JsonSchemaComposite> {

        @Override
        public void serialize(JsonSchemaComposite value, JsonGenerator gen, SerializationContext ctxt) {
            ObjectNode object = JsonNodeFactory.instance.objectNode();

            for (var schema : value.getSchemas()) {
                object.setAll((ObjectNode) ctxt.valueToTree(schema));
            }
            gen.writeTree(object);
        }
    }
}
