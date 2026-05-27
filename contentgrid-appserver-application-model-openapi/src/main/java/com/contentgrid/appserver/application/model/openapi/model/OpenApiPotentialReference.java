package com.contentgrid.appserver.application.model.openapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Interface that marks an object for potentially being substituted by a reference object {@link OpenApiReference}
 * @param <T> The type that is referenced by the reference
 */
public interface OpenApiPotentialReference<T extends OpenApiPotentialReference<T>> {

    /**
     * @return The original object that is potentially referenced
     */
    @JsonIgnore
    default T getOriginalObject() {
        return (T)this;
    }
}
