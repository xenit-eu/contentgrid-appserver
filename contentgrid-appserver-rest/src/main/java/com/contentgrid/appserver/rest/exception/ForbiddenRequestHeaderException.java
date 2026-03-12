package com.contentgrid.appserver.rest.exception;

import lombok.Getter;

@Getter
public class ForbiddenRequestHeaderException extends Exception {

    private final String header;

    public ForbiddenRequestHeaderException(String header) {
        super();
        this.header = header;
    }
}
