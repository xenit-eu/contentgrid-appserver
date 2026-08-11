package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.utils.PartialContentInputStream;
import java.io.InputStream;
import lombok.NonNull;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class S3ContentReader extends S3ContentAccessor implements ContentReader {

    @NonNull
    private final ResponseInputStream<GetObjectResponse> response;

    private final ResolvedContentRange contentRange;

    public S3ContentReader(@NonNull ContentReference reference,
            @NonNull ResponseInputStream<GetObjectResponse> response, ResolvedContentRange contentRange) {
        super(reference);
        this.response = response;
        this.contentRange = contentRange;
    }

    @Override
    public InputStream getContentInputStream() {
        if (contentRange != null) {
            return PartialContentInputStream.fromContentRange(response, contentRange);
        }
        return response;
    }
}
