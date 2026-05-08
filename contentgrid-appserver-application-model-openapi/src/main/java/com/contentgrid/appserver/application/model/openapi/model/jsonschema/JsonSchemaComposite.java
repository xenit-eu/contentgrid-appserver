package com.contentgrid.appserver.application.model.openapi.model.jsonschema;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaComposite.Serializer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
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

    static class Serializer extends JsonSerializer<JsonSchemaComposite> {

        @Override
        public void serialize(JsonSchemaComposite value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            var mapper = (ObjectMapper) gen.getCodec();
            ObjectNode object = mapper.createObjectNode();

            for (var schema : value.getSchemas()) {
                object.setAll((ObjectNode) mapper.valueToTree(schema));
            }
            gen.writeTree(object);
        }
    }
}
