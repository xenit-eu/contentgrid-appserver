package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Value
public class OpenApiHttpHeaders {
    @JsonValue
    Map<String, OpenApiPotentialReference<OpenApiHeaderDescription>> items = new LinkedHashMap<>();

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
        Object example;
    }
}
