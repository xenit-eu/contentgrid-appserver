package com.contentgrid.appserver.rest.exception;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class InvalidRelationTargetException extends Exception {
    @Getter
    @NonNull
    private final String reference;

    @Override
    public String getMessage() {
        return "Invalid relation target: '%s'".formatted(reference);
    }
}

