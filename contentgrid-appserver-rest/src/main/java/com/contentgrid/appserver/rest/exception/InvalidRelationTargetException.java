package com.contentgrid.appserver.rest.exception;

import lombok.Getter;
import lombok.NonNull;

public class InvalidRelationTargetException extends Exception {
    @Getter
    @NonNull
    private final String reference;

    public InvalidRelationTargetException(@NonNull String reference) {
        super("Invalid relation target: '%s'".formatted(reference));
        this.reference = reference;
    }
}

