package com.contentgrid.appserver.contentstore.impl.encryption;

import com.contentgrid.appserver.contentstore.api.ContentReference;

public class NoEncryptableDataEncryptionKeysException extends UnencryptableContentException {

    public NoEncryptableDataEncryptionKeysException(ContentReference contentReference) {
        super(contentReference, "No wrappers can encrypt data encryption keys");
    }
}
