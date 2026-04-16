package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Value
public class OpenApiPaths {
    @JsonAnyGetter
    Map<String, OpenApiPotentialReference<OpenApiPathItem>> items = new LinkedHashMap<>();

    public OpenApiPathItem path(@NonNull String path) {
        return (OpenApiPathItem) items.computeIfAbsent(path, _unused -> new OpenApiPathItem());
    }

    public OpenApiOperation create(@NonNull HttpMethod method, @NonNull String path) {
        return path(path).method(method);
    }


    @Data
    @Accessors(chain = true)
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class OpenApiPathItem implements OpenApiPotentialReference<OpenApiPathItem> {
        @JsonInclude(Include.NON_EMPTY)
        String summary;
        @JsonInclude(Include.NON_EMPTY)
        String description;

        @JsonAnyGetter
        final Map<HttpMethod, OpenApiOperation> operations = new LinkedHashMap<>();

        @JsonInclude(Include.NON_EMPTY)
        List<OpenApiParameter> parameters;

        public OpenApiOperation method(@NonNull HttpMethod method) {
            return operations.computeIfAbsent(method, (_unused) -> new OpenApiOperation());
        }

        public OpenApiPathItem method(@NonNull HttpMethod method, Consumer<OpenApiOperation> consumer) {
            consumer.accept(method(method));
            return this;
        }

        public OpenApiPathItem each(BiConsumer<HttpMethod, OpenApiOperation> consumer) {
            operations.forEach(consumer);
            return this;
        }
    }

    public enum HttpMethod {
        GET,
        PUT,
        POST,
        DELETE,
        PATCH
    }
}
