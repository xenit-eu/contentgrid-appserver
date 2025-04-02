package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when an attribute does not support a flag.
 */
public class InvalidFlagException extends ApplicationModelException {

    /**
     * Constructs an InvalidFlagException with the specified message.
     *
     * @param message the detail message
     */
    public InvalidFlagException(String message) {
        super(message);
    }
}