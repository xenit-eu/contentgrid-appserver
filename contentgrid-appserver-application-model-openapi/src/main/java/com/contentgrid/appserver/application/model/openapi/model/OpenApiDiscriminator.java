package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import java.util.Map;
import java.util.TreeMap;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

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

    private static class DiscriminatorConverter implements Converter<OpenApiReference<JsonSchema>, String> {

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
