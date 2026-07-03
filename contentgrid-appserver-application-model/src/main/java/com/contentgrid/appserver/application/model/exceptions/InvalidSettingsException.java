package com.contentgrid.appserver.application.model.exceptions;

/**
 * Exception thrown when invalid application settings are defined.
 */
public class InvalidSettingsException extends ApplicationModelException {

    /**
     * Constructs an {@link InvalidSettingsException} with the specified message.
     *
     * @param message the detail message
     */
    public InvalidSettingsException(String message) {
        super(message);
    }
}