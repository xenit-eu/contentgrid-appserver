package com.contentgrid.appserver.contentstore.impl.encryption;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import lombok.NonNull;

public class UnencryptableContentException extends UnwritableContentException {

    public UnencryptableContentException(@NonNull ContentReference reference, @NonNull String message) {
        super(reference, "Can not encrypt: %s".formatted(message));
    }

}
