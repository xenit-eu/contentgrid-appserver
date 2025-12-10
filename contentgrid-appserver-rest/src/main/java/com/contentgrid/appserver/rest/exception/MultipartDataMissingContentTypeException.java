package com.contentgrid.appserver.rest.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MultipartDataMissingContentTypeException extends Exception {
    @Getter
    private final String fieldName;

    @Override
    public String getMessage() {
        return "File field '%s' must have a Content-Type specified".formatted(fieldName);
    }
}
