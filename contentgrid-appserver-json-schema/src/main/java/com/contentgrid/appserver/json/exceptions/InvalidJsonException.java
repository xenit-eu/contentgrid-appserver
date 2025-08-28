package com.contentgrid.appserver.json.exceptions;

public abstract sealed class InvalidJsonException extends Exception permits AttributeNotFoundException,
        InvalidAttributeTypeException, SchemaValidationException, UnknownFilterTypeException, UnknownFlagException {

    protected InvalidJsonException(String s) {
        super(s);
    }
}
