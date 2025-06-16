package com.contentgrid.appserver.rest.exception;

public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String mediaType) {
        super("Media type %s not supported".formatted(mediaType));
    }

}
