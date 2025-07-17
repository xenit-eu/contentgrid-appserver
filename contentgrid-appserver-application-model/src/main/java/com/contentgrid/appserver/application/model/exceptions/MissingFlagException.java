package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an attribute is missing a flag.
 */
public class MissingFlagException extends ApplicationModelException {

    /**
     * Constructs an MissingFlagException with the specified message.
     *
     * @param message the detail message
     */
    public MissingFlagException(String message) {
        super(message);
    }
}