package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
class S3ContentAccessor implements ContentAccessor {

    @NonNull
    private final ContentReference reference;
    private final long contentSize;

    @Override
    public String getDescription() {
        return "S3 object [%s]".formatted(reference);
    }
}
