package com.contentgrid.appserver.application.model.json.exceptions;

public final class SchemaValidationException extends InvalidJsonException {

    public SchemaValidationException(String message) {
        super(message);
    }
}
