package com.contentgrid.appserver.exception;

import lombok.Getter;

@Getter
public class InvalidPaginationParameterException extends RuntimeException {

    private final String parameter;
    private final String value;
    private final String detail;

    public InvalidPaginationParameterException(String parameter, String value, String detail) {
        super("Pagination parameter '%s'='%s' is invalid: %s".formatted(parameter, value, detail));
        this.parameter = parameter;
        this.value = value;
        this.detail = detail;
    }

}