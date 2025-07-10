package com.contentgrid.appserver.content.api;

import java.io.IOException;
import lombok.Getter;
import lombok.NonNull;

/**
 * IO exception during content operations
 */
@Getter
public class ContentIOException extends Exception {

    @NonNull
    private final ContentReference reference;

    public ContentIOException(@NonNull ContentReference reference, String message) {
        super("Content '%s': %s".formatted(reference, message));
        this.reference = reference;
    }

    public ContentIOException(@NonNull ContentReference reference, @NonNull IOException cause) {
        this(reference, cause.getMessage());
        initCause(cause);
    }
}
