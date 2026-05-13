package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

/**
 * A simple object to allow referencing other components in the OpenAPI Description
 * @see <a href="https://spec.openapis.org/oas/v3.2.0.html#reference-object">Reference Object</a>
 */
@Value
public class OpenApiReference<T extends OpenApiPotentialReference<T>> implements OpenApiPotentialReference<T> {
    @JsonProperty("$ref")
    String reference;

    @Getter(AccessLevel.NONE)
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    Supplier<T> originalObjectSupplier;

    @Override
    public T getOriginalObject() {
        return originalObjectSupplier.get();
    }
}
