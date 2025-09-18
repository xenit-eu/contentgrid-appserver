package com.contentgrid.appserver.contentstore.api;

import lombok.NonNull;

/**
 * Exception thrown when content can not be read for any reason
 */
public class UnreadableContentException extends ContentIOException {

    public UnreadableContentException(@NonNull ContentReference reference) {
        super(reference, "Can not be read");
    }

    public UnreadableContentException(@NonNull ContentReference reference, @NonNull String message) {
        super(reference, "Can not be read: %s".formatted(message));
    }

    public UnreadableContentException(@NonNull ContentReference reference, @NonNull Throwable cause) {
        this(reference, cause.getMessage());
        initCause(cause);
    }
}
