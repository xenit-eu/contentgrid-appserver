package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        final List<OpenApiPotentialReference<OpenApiParameter>> parameters = new ArrayList<>();

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

        public OpenApiPathItem combineParameters() {
            List<OpenApiPotentialReference<OpenApiParameter>> commonParameters = null;

            // First, collect all common parameters
            for (var operation : operations.values()) {
                if (commonParameters == null) {
                    commonParameters = new ArrayList<>(operation.getParameters());
                }
                commonParameters.retainAll(operation.getParameters());
            }

            // Next, add them to the stored common parameters
            if (commonParameters != null) {
                parameters.addAll(commonParameters);
            }

            // Finally, clear up common parameters from operations
            for (var operation: operations.values()) {
                operation.getParameters().removeAll(commonParameters);
            }

            return this;
        }

        public OpenApiPathItem parameter(@NonNull OpenApiPotentialReference<OpenApiParameter> parameter) {
            parameters.add(parameter);
            return this;
        }
    }

    public enum HttpMethod {
        GET,
        PUT,
        POST,
        DELETE,
        PATCH;

        @JsonValue
        public String getJsonValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
