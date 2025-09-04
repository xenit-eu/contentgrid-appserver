package com.contentgrid.appserver.exception;

import java.util.Arrays;
import java.util.stream.Stream;

public class InvalidSortParameterException extends RuntimeException {

    public InvalidSortParameterException(String message) {
        super(message);
    }

    public static InvalidSortParameterException invalidDirection(String direction) {
        return new InvalidSortParameterException("Invalid sort direction '" + direction + "'. Valid values are 'asc' and 'desc'");
    }

    public static InvalidSortParameterException invalidField(String fieldName, String entityName) {
        return new InvalidSortParameterException("Sortable field '" + fieldName + "' not found on entity '" + entityName + "'");
    }

    public Stream<InvalidSortParameterException> allExceptions() {
        return Stream.concat(
                Stream.of(this),
                Arrays.stream(getSuppressed())
                        .filter(InvalidSortParameterException.class::isInstance)
                        .map(InvalidSortParameterException.class::cast)
                        .flatMap(InvalidSortParameterException::allExceptions)
        );
    }

}