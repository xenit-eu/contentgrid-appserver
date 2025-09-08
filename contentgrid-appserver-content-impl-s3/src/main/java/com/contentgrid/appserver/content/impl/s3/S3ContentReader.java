package com.contentgrid.appserver.content.impl.s3;

import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.impl.utils.PartialContentInputStream;
import io.minio.GetObjectResponse;
import java.io.InputStream;
import lombok.NonNull;

public class S3ContentReader extends S3ContentAccessor implements ContentReader {

    @NonNull
    private final GetObjectResponse response;

    public S3ContentReader(@NonNull GetObjectResponse response) {
        super(ContentReference.of(response.object()), contentSize(response));
        this.response = response;
    }

    private static long contentSize(GetObjectResponse response) {
        var contentRange = response.headers().get("Content-Range");
        if (contentRange != null) {
            return Long.parseLong(contentRange.split("/", 2)[1]);
        }
        return Long.parseLong(response.headers().get("Content-Length"));
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
