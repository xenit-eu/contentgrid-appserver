package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.OpenApiPathItem;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
public class OpenApiComponents {
    OpenApiComponentRegistry<JsonSchema> schemas = new OpenApiComponentRegistry<>("#/components/schemas/");
    OpenApiComponentRegistry<OpenApiPathItem> pathItems = new OpenApiComponentRegistry<>("#/components/pathItems/");

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class OpenApiComponentRegistry<T extends OpenApiPotentialReference<T>> {
        @NonNull
        String referencePrefix;

        @JsonAnyGetter
        final Map<String, T> items = new LinkedHashMap<>();

        public OpenApiPotentialReference<T> register(@NonNull String name, @NonNull Supplier<T> factory) {
            items.computeIfAbsent(name, _unused -> factory.get());
            return new OpenApiReference<T>(referencePrefix + name);
        }

        public OpenApiPotentialReference<T> register(@NonNull String name, @NonNull T schema) {
            return register(name, () -> schema);
        }
    }
}
