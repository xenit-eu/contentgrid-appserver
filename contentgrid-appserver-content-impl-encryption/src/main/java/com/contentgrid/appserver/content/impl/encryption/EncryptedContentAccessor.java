package com.contentgrid.appserver.content.impl.encryption;

import com.contentgrid.appserver.content.api.ContentAccessor;
import com.contentgrid.appserver.content.api.ContentReference;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class EncryptedContentAccessor implements ContentAccessor {

    @NonNull
    private final ContentAccessor accessor;
    @Getter
    private final long contentSize;

    @Override
    public ContentReference getReference() {
        return accessor.getReference();
    }

    @Override
    public String getDescription() {
        return "Encrypted %s".formatted(accessor);
    }
}
