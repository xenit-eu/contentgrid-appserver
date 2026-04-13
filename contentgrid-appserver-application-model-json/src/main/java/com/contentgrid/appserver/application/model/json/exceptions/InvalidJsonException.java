package com.contentgrid.appserver.application.model.json.exceptions;

public abstract sealed class InvalidJsonException extends Exception permits AttributeNotFoundException,
        InvalidAttributeTypeException, SchemaValidationException, UnknownFilterTypeException, UnknownFlagException {

    protected InvalidJsonException(String s) {
        super(s);
    }
}
