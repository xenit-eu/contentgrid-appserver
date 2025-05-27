package com.contentgrid.appserver.json.exceptions;

public abstract sealed class InValidJsonException extends Exception permits AttributeNotFoundException,
        EntityNotFoundException, InvalidAttributeTypeException, SchemaValidationException, UnknownFilterTypeException,
        UnknownFlagException {

    protected InValidJsonException(String s) {
        super(s);
    }
}
