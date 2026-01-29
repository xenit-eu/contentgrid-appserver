package com.contentgrid.appserver.contentstore.impl.encryption;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class EncryptedContentAccessor implements ContentAccessor {

    @NonNull
    private final ContentAccessor accessor;

    @Override
    public ContentReference getReference() {
        return accessor.getReference();
    }

    @Override
    public String getDescription() {
        return "Encrypted %s".formatted(accessor);
    }
}
