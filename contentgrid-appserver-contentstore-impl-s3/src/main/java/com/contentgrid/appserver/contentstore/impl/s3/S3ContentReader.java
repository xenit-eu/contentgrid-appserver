package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
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
    public InputStream getContentInputStream() {
        var getObjectResponse = response.response();
        var contentRange = getObjectResponse.contentRange();
        // partsCount is only present on the SDK's own internal part requests: it turns every full
        // (unranged) getObject into partNumber requests and reassembles all parts into one stream, but
        // attaches part 1's response to it. Wrapping with that Content-Range would zero-fill everything
        // past part 1.
        if (contentRange != null && getObjectResponse.partsCount() == null) {
            return PartialContentInputStream.fromContentRange(response, contentRange);
        }
        return response;
    }
}
