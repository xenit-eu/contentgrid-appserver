package com.contentgrid.appserver.rest.exception;

import java.util.Map;
import lombok.Getter;

public class AttributesValidationException extends IllegalArgumentException {

    @Getter
    private final Map<String, String> validationErrors;

    public AttributesValidationException(Map<String, String> validationErrors) {
        this.validationErrors = Map.copyOf(validationErrors);
    }

}