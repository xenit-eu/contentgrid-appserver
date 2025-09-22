package com.contentgrid.appserver.contentstore.impl.fs;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
class FileContentAccessor implements ContentAccessor {

    @NonNull
    private final ContentReference reference;
    private final long contentSize;

    @Override
    public String getDescription() {
        return "File [%s]".formatted(getReference());
    }
}
