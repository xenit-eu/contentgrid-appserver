package com.contentgrid.appserver.application.model.openapi.model;

/**
 * Interface that marks an object for potentially being substituted by a reference object {@link OpenApiReference}
 * @param <T> The type that is referenced by the reference
 */
public interface OpenApiPotentialReference<T extends OpenApiPotentialReference<T>> {

}
