package com.contentgrid.appserver.application.model.openapi.model;

import com.contentgrid.appserver.application.model.openapi.model.OpenApiPaths.OpenApiPathItem;
import com.contentgrid.appserver.application.model.openapi.model.jsonschema.JsonSchema;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    OpenApiComponentRegistry<OpenApiPathItem> pathItems = new OpenApiComponentRegistry<>("#/components/pathItems/");

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class OpenApiComponentRegistry<T extends OpenApiPotentialReference<T>> {
        @NonNull
        @JsonIgnore
        String referencePrefix;

        @JsonAnyGetter
        final Map<String, T> items = new LinkedHashMap<>();

        public Map<String, T> getItems() {
            return Collections.unmodifiableMap(items);
        }

        public T getItem(String key) {
            return items.get(key);
        }

        public OpenApiPotentialReference<T> register(@NonNull String name, @NonNull Supplier<T> factory) {
            return register(name, self -> factory.get());
        }

        public OpenApiPotentialReference<T> register(@NonNull String name, @NonNull Function<OpenApiPotentialReference<T>, T> factory) {
            var reference = new OpenApiReference<T>(referencePrefix + name);
            if(!items.containsKey(name)) {
                items.put(name, factory.apply(reference));
            }
            return reference;
        }

        public OpenApiPotentialReference<T> register(@NonNull String name, @NonNull T schema) {
            return register(name, () -> schema);
        }
    }
}
