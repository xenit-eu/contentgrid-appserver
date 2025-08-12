package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when a referenced attribute is not found.
 */
public class AttributeNotFoundException extends ApplicationModelException {

    /**
     * Constructs an AttributeNotFoundException with the specified message.
     *
     * @param message the detail message
     */
    public AttributeNotFoundException(String message) {
        super(message);
    }
}