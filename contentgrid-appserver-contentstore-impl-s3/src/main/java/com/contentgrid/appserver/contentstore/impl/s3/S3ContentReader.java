package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.impl.utils.PartialContentInputStream;
import java.io.InputStream;
import lombok.NonNull;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public class S3ContentReader extends S3ContentAccessor implements ContentReader {

    @NonNull
    private final ResponseInputStream<GetObjectResponse> response;

    public S3ContentReader(@NonNull ContentReference reference,
            @NonNull ResponseInputStream<GetObjectResponse> response) {
        super(reference);
        this.response = response;
    }

    @Override
    public InputStream getContentInputStream() throws UnreadableContentException {
        var contentRange = response.response().contentRange();
        if (contentRange != null) {
            return PartialContentInputStream.fromContentRange(response, contentRange);
        }
        return response;
    }
}
