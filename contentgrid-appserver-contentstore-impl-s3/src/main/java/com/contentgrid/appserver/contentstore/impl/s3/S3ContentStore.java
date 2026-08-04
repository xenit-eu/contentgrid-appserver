package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.utils.GuardedContentReader;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RequiredArgsConstructor
public class S3ContentStore implements ContentStore {

    @NonNull
    private final S3Client client;

    @NonNull
    private final S3AsyncClient asyncClient;

    @NonNull
    private final String bucketName;

    private static final String CONTENT_TYPE = "application/octet-stream";

    @Override
    public ContentReader getReader(@NonNull ContentReference contentReference, ResolvedContentRange contentRange)
            throws UnreadableContentException {

        try {
            var requestBuilder = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(contentReference.getValue());
            if (contentRange != null) {
                requestBuilder.range("bytes=%d-%d".formatted(contentRange.getStartByte(),
                        contentRange.getEndByteInclusive()));
            }
            var object = client.getObject(requestBuilder.build());

            if (contentRange != null && contentSize(object.response()) != contentRange.getContentSize()) {
                object.abort();
                throw new UnreadableContentException(contentReference, "range size does not match actual size");
            }

            var reader = new S3ContentReader(contentReference, object);

            return new GuardedContentReader(reader);
        } catch (SdkException e) {
            throw new UnreadableContentException(contentReference, e);
        }
    }

    private static long contentSize(GetObjectResponse response) {
        var contentRange = response.contentRange();
        if (contentRange != null) {
            return Long.parseLong(contentRange.split("/", 2)[1]);
        }
        return response.contentLength();
    }

    @Override
    public ContentAccessor writeContent(@NonNull InputStream inputStream) throws UnwritableContentException {
        var contentReference = ContentReference.of(UUID.randomUUID().toString());
        var requestBody = AsyncRequestBody.forBlockingInputStream(null); // length unknown
        var response = asyncClient.putObject(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(contentReference.getValue())
                        .contentType(CONTENT_TYPE)
                        .build(),
                requestBody);

        // writeInputStream can hang if the upload fails partway (a BlockingInputStreamAsyncRequestBody
        // limitation), so it runs on its own thread, interrupted once `response` completes.
        var writer = new Thread(() -> {
            try {
                requestBody.writeInputStream(inputStream);
            } catch (RuntimeException e) {
                // The same failure surfaces through `response` below; nothing more to do here
            }
        }, "s3-content-writer-" + contentReference.getValue());
        writer.setDaemon(true);
        writer.start();

        try {
            response.join();
            return new S3ContentAccessor(contentReference);
        } catch (CompletionException e) {
            throw new UnwritableContentException(contentReference, e.getCause());
        } catch (RuntimeException e) {
            throw new UnwritableContentException(contentReference, e);
        } finally {
            writer.interrupt();
        }
    }

    @Override
    public void remove(@NonNull ContentReference contentReference) throws UnwritableContentException {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(contentReference.getValue())
                    .build());
        } catch (SdkException e) {
            throw new UnwritableContentException(contentReference, e);
        }
    }

}
