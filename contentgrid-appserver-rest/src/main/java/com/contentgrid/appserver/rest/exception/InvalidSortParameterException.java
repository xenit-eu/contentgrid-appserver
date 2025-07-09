package com.contentgrid.appserver.rest.exception;

import lombok.Getter;

/**
 * Exception thrown when sort parameter is not correctly parsed
 */
public class InvalidSortParameterException extends RuntimeException {

    @Getter
    private final String direction;

    public InvalidSortParameterException(String direction) {
        super("Invalid sort direction '" + direction + "'. Valid values are 'asc' and 'desc'");
        this.direction = direction;
    }

}