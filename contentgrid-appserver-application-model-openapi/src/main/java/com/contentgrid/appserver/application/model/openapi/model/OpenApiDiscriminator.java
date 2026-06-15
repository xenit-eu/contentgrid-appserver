package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.type.TypeFactory;
import tools.jackson.databind.util.StdConverter;
import java.util.Map;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

/**
 * A polymorphic schema MAY include a Discriminator Object,
 * which defines the name of the property that may be used as a hint for which schema of the anyOf or oneOf,
 * or which schema that references the current schema in an allOf,
 * is expected to validate the structure of the model.
 * @see <a href="https://spec.openapis.org/oas/v3.2.0.html#discriminator-object">Discriminator Object</a>
 */
@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class OpenApiDiscriminator {

    @NonNull
    String propertyName;

    @JsonSerialize(contentConverter = DiscriminatorConverter.class)
    Map<String, OpenApiReference<JsonSchema>> mapping = new TreeMap<>();

    @JsonSerialize(converter =  DiscriminatorConverter.class)
    @JsonInclude(Include.NON_NULL)
    OpenApiReference<JsonSchema> defaultMapping;

    public OpenApiDiscriminator mapping(String mappingKey, OpenApiReference<JsonSchema> mapping) {
        this.mapping.put(mappingKey, mapping);
        return this;
    }

    /**
     * Converts a reference object to just its reference string,
     * as the discriminator definition requires a direct reference, without a surrounding {@code $ref} object
     */
    private static class DiscriminatorConverter extends StdConverter<OpenApiReference<JsonSchema>, String> {

        @Override
        public String convert(OpenApiReference<JsonSchema> value) {
            return value.getReference();
        }

        @Override
        public JavaType getInputType(TypeFactory typeFactory) {
            return typeFactory.constructType(OpenApiDiscriminator.class);
        }

        @Override
        public JavaType getOutputType(TypeFactory typeFactory) {
            return typeFactory.constructType(String.class);
        }
    }

}
