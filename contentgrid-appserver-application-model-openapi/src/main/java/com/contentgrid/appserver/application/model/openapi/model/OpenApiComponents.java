package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.OpenApiPathItem;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
public class OpenApiComponents {
    OpenApiComponentRegistry<JsonSchema> schemas = new OpenApiComponentRegistry<>("#/components/schemas/");
    OpenApiComponentRegistry<OpenApiResponse> responses = new OpenApiComponentRegistry<>("#/components/responses/");
    OpenApiComponentRegistry<OpenApiParameter> parameters = new OpenApiComponentRegistry<>("#/components/parameters/");
    OpenApiComponentRegistry<OpenApiHttpHeaders.OpenApiHeaderDescription> headers =  new OpenApiComponentRegistry<>("#/components/headers/");
    OpenApiComponentRegistry<OpenApiPathItem> pathItems = new OpenApiComponentRegistry<>("#/components/pathItems/");

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class OpenApiComponentRegistry<T extends OpenApiPotentialReference<T>> {
        @NonNull
        @JsonIgnore
        String referencePrefix;

        @JsonAnyGetter
        Map<String, T> items = new TreeMap<>();

        public Map<String, T> getItems() {
            return Collections.unmodifiableMap(items);
        }

        public T getItem(String key) {
            return items.get(key);
        }

        public OpenApiReference<T> register(@NonNull String name, @NonNull Supplier<T> factory) {
            return register(name, self -> factory.get());
        }

        public OpenApiReference<T> register(@NonNull String name, @NonNull Function<OpenApiPotentialReference<T>, T> factory) {
            var reference = new OpenApiReference<T>(referencePrefix + name, () -> items.get(name));
            if(!items.containsKey(name)) {
                items.put(name, factory.apply(reference));
            }
            return reference;
        }

        public OpenApiReference<T> register(@NonNull String name, @NonNull T schema) {
            return register(name, () -> schema);
        }

    }
}
