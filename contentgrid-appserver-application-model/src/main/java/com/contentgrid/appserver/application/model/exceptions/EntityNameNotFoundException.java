package com.contentgrid.appserver.application.model.exceptions;

import com.contentgrid.appserver.application.model.values.EntityName;

/**
 * Exception thrown when an entity by the given name doesn't exist in the Application.
 */
public class EntityNameNotFoundException extends ApplicationModelException {

    public EntityNameNotFoundException(String message) {
        super(message);
    }

    public EntityNameNotFoundException(EntityName name) {
        this("Entity '%s' not found".formatted(name));
    }
}