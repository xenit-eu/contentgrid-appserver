package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchemaOneOf;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Value
public class OpenApiHttpHeaders {
    @JsonValue
    Map<String, OpenApiPotentialReference<OpenApiHeaderDescription>> items = new TreeMap<>();

    public OpenApiHttpHeaders header(String name, OpenApiPotentialReference<OpenApiHeaderDescription> header) {
        items.put(name, header);
        return this;
    }

    public OpenApiHttpHeaders header(String name, Consumer<OpenApiHeaderDescription> consumer) {
        var description = new OpenApiHeaderDescription();
        consumer.accept(description);
        items.put(name, description);
        return this;
    }

    public OpenApiHttpHeaders combinedWith(OpenApiHttpHeaders headers) {
        headers.items.forEach((name, header) -> {
            items.putIfAbsent(name, header);
            items.computeIfPresent(name, (n, existing) -> existing.getOriginalObject().combinedWith(existing.getOriginalObject()));
        });
        return this;
    }

    @Data
    @Accessors(chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OpenApiHeaderDescription implements OpenApiPotentialReference<OpenApiHeaderDescription> {
        @JsonInclude(Include.NON_NULL)
        String description;
        @JsonInclude(Include.NON_DEFAULT)
        boolean required;
        @JsonInclude(Include.NON_DEFAULT)
        boolean deprecated;

        @JsonInclude(Include.NON_NULL)
        OpenApiPotentialReference<JsonSchema> schema;

        public OpenApiHeaderDescription combinedWith(OpenApiHeaderDescription header) {
            if(header.description != null) {
                description = header.description;
            }
            required = header.required || required;
            deprecated = header.deprecated || deprecated;
            if(schema != null && header.schema != null) {
                schema = new JsonSchemaOneOf(schema, header.schema);
            } else if(header.schema != null) {
                schema = header.schema;
            }
            return this;
        }
    }
}
