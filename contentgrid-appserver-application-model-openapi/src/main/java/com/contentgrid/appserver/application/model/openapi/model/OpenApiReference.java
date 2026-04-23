package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Value;

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
