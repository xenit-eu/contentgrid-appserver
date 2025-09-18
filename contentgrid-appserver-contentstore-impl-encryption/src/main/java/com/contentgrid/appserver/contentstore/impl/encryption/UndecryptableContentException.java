package com.contentgrid.appserver.contentstore.impl.encryption;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class UndecryptableContentException extends UnreadableContentException {

    public UndecryptableContentException(
            @NonNull ContentReference reference,
            @NonNull String message
    ) {
        super(reference, "Can not decrypt: "+message);
    }

    public UndecryptableContentException(
            @NonNull ContentReference reference,
            @NonNull Throwable cause
    ) {
        this(reference, cause.getMessage());
        initCause(cause);
    }

}
