package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an invalid type is provided to an attribute.
 */
public class InvalidAttributeTypeException extends ApplicationModelException {

    /**
     * Constructs an InvalidAttributeTypeException with the specified message.
     *
     * @param message the detail message
     */
    public InvalidAttributeTypeException(String message) {
        super(message);
    }
}