package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.impl.utils.PartialContentInputStream;
import io.minio.GetObjectResponse;
import java.io.InputStream;
import lombok.NonNull;

public class S3ContentReader extends S3ContentAccessor implements ContentReader {

    @NonNull
    private final GetObjectResponse response;

    public S3ContentReader(@NonNull GetObjectResponse response) {
        super(ContentReference.of(response.object()));
        this.response = response;
    }


    @Override
    public InputStream getContentInputStream() throws UnreadableContentException {
        var contentRange = response.headers().get("Content-Range");
        if (contentRange != null) {
            return PartialContentInputStream.fromContentRange(response, contentRange);
        }
        return response;
    }
}
