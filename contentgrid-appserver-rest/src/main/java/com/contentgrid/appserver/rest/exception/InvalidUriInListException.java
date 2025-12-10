package com.contentgrid.appserver.rest.exception;

import lombok.Getter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;

public class InvalidUriInListException extends HttpMessageNotReadableException {
    @Getter
    private final String invalid;

    public InvalidUriInListException(String invalid, Throwable cause, HttpInputMessage httpInputMessage) {
        super("Invalid URI in text/uri-list: " + invalid, cause, httpInputMessage);
        this.invalid = invalid;
    }
}
