package com.contentgrid.appserver.rest.exception;

import lombok.Getter;

@Getter
public class UnsupportedRequestHeaderException extends Exception {

    private final String header;

    public UnsupportedRequestHeaderException(String header) {
        super();
        this.header = header;
    }
}
