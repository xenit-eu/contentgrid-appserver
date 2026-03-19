package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.utils.GuardedContentReader;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioAsyncClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.MinioException;
import io.minio.errors.XmlParserException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class S3ContentStore implements ContentStore {

    @NonNull
    private final MinioAsyncClient client;

    @NonNull
    private final String bucketName;

    /**
     * Size of a part to be uploaded to S3.
     * <p>
     * Its value must be between 5 MiB and 5 GiB
     * <p>
     * It is a trade-off between:
     * - memory usage for buffers on our side
     * - overhead for multipart uploads
     * - the maximum size of an object that can be uploaded
     * S3 enforces a maximum size of 10000 parts for a multipart upload
     * <p>
     * With this setting of 50 MiB, this would limit the maximal filesize to slightly over 0.5TB,
     * which should be sufficient.
     *
     * @see io.minio.ObjectWriteArgs#MAX_MULTIPART_COUNT
     * @see io.minio.ObjectWriteArgs#MIN_MULTIPART_SIZE
     * @see io.minio.ObjectWriteArgs#MAX_PART_SIZE
     */
    private static final int PART_SIZE = 50*1024*1024; // 50 MiB

    @Override
    @SneakyThrows(InterruptedException.class)
    public ContentReader getReader(@NonNull ContentReference contentReference, ResolvedContentRange contentRange)
            throws UnreadableContentException {

        try {
            var getObjectArgsBuilder = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(contentReference.getValue());
            if (contentRange != null) {
                getObjectArgsBuilder = getObjectArgsBuilder.offset(contentRange.getStartByte())
                        .length(contentRange.getRangeSize());
            }
            var object = client.getObject(getObjectArgsBuilder.build()).get();

            if(contentRange != null && contentSize(object) != contentRange.getContentSize()) {
                throw new UnreadableContentException(contentReference, "range size does not match actual size");
            }

            var reader = new S3ContentReader(object);

            return new GuardedContentReader(reader);
        } catch(MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException | ExecutionException e) {
            throw new UnreadableContentException(contentReference, e);
        }
    }

    private static long contentSize(GetObjectResponse response) {
        var contentRange = response.headers().get("Content-Range");
        if (contentRange != null) {
            return Long.parseLong(contentRange.split("/", 2)[1]);
        }
        return Long.parseLong(response.headers().get("Content-Length"));
    }

    @Override
    @SneakyThrows(InterruptedException.class)
    public ContentAccessor writeContent(@NonNull InputStream inputStream) throws UnwritableContentException {
        var contentReference = ContentReference.of(UUID.randomUUID().toString());
        try {
            client.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(contentReference.getValue())
                            .stream(inputStream, -1, PART_SIZE)
                            .build())
                    .get();
            return new S3ContentAccessor(contentReference);
        } catch (InsufficientDataException | InternalException | InvalidKeyException | IOException |
                 NoSuchAlgorithmException | XmlParserException | ExecutionException e) {
            throw new UnwritableContentException(contentReference, e);
        }
    }

    @Override
    @SneakyThrows(InterruptedException.class)
    public void remove(@NonNull ContentReference contentReference) throws UnwritableContentException {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(contentReference.getValue())
                    .build())
                    .get();
        } catch (InsufficientDataException | InternalException | InvalidKeyException | IOException |
                 NoSuchAlgorithmException | XmlParserException | ExecutionException e) {
            throw new UnwritableContentException(contentReference, e);
        }

    }

}
