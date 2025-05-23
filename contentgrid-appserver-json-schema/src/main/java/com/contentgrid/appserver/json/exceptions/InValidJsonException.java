package com.contentgrid.appserver.json.exceptions;

public abstract sealed class InValidJsonException extends Exception permits SchemaValidationException, UnknownFlagException {

    public InValidJsonException(String s) {
        super(s);
    }
}
