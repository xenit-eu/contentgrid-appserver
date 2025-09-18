package com.contentgrid.appserver.contentstore.api;

import lombok.NonNull;

/**
 * Exception thrown when content can not be written for any reason
 */
public class UnwritableContentException extends ContentIOException {

    public UnwritableContentException(@NonNull ContentReference reference) {
        super(reference, "Can not be written");
    }

    public UnwritableContentException(@NonNull ContentReference reference, @NonNull String message) {
        super(reference, "Can not be written: %s".formatted(message));
    }

    public UnwritableContentException(@NonNull ContentReference reference, @NonNull Throwable cause) {
        this(reference);
        initCause(cause);
    }
}
