package com.contentgrid.appserver.exception;

import lombok.Getter;
import lombok.NonNull;

@Getter
public class InvalidPaginationParameterException extends RuntimeException {

    @NonNull
    private final String parameter;
    @NonNull
    private final String value;
    @NonNull
    private final String detail;

    public InvalidPaginationParameterException(String parameter, String value, String detail) {
        super("Pagination parameter '%s'='%s' is invalid: %s".formatted(parameter, value, detail));
        this.parameter = parameter;
        this.value = value;
        this.detail = detail;
    }

}