package com.contentgrid.appserver.exception;

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

}