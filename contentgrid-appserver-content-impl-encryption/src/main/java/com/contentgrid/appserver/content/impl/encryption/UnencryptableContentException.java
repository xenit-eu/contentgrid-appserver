package com.contentgrid.appserver.content.impl.encryption;

import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import lombok.NonNull;

public class UnencryptableContentException extends UnwritableContentException {

    public UnencryptableContentException(@NonNull ContentReference reference, @NonNull String message) {
        super(reference, "Can not encrypt: %s".formatted(message));
    }

}
