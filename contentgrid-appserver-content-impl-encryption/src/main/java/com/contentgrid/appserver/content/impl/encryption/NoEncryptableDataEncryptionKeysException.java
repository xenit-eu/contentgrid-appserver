package com.contentgrid.appserver.content.impl.encryption;

import com.contentgrid.appserver.content.api.ContentReference;

public class NoEncryptableDataEncryptionKeysException extends UnencryptableContentException {

    public NoEncryptableDataEncryptionKeysException(ContentReference contentReference) {
        super(contentReference, "No wrappers can encrypt data encryption keys");
    }
}
