package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an entity by the given name doesn't exist in the Application.
 */
public class EntityNameNotFoundException extends ApplicationModelException {

    public EntityNameNotFoundException(String message) {
        super(message);
    }
}