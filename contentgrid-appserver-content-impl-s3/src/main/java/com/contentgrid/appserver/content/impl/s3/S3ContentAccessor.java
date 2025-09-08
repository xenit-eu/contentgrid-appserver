package com.contentgrid.appserver.content.impl.s3;

import com.contentgrid.appserver.content.api.ContentAccessor;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.impl.utils.CountingInputStream;
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
