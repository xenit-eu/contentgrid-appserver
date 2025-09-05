package com.contentgrid.appserver.content.impl.s3;

import com.contentgrid.appserver.content.api.ContentAccessor;
import com.contentgrid.appserver.content.api.ContentReader;
import com.contentgrid.appserver.content.api.ContentReference;
import com.contentgrid.appserver.content.api.ContentStore;
import com.contentgrid.appserver.content.api.UnreadableContentException;
import com.contentgrid.appserver.content.api.UnwritableContentException;
import com.contentgrid.appserver.content.api.range.ResolvedContentRange;
import com.contentgrid.appserver.content.impl.utils.CountingInputStream;
import com.contentgrid.appserver.content.impl.utils.GuardedContentReader;
import io.minio.GetObjectArgs;
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
    public ContentReader getReader(ContentReference contentReference, ResolvedContentRange contentRange)
            throws UnreadableContentException {

        try {
            var object = client.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(contentReference.getValue())
                            .offset(contentRange.getStartByte())
                            .length(contentRange.getRangeSize())
                            .build()
            ).get();

            var reader = new S3ContentReader(object);

            if(reader.getContentSize() != contentRange.getContentSize()) {
                throw new UnreadableContentException(contentReference, "range size does not match actual size");
            }

            return new GuardedContentReader(reader);
        } catch(MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException | ExecutionException e) {
            throw new UnreadableContentException(contentReference, e);
        }
    }

    @Override
    public ContentAccessor writeContent(InputStream inputStream) throws UnwritableContentException {
        var contentReference = ContentReference.of(UUID.randomUUID().toString());
        var countingInputStream = new CountingInputStream(inputStream);
        try {
            client.putObject(PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(contentReference.getValue())
                            .stream(countingInputStream, -1, PART_SIZE)
                            .build())
                    .join();
            return new S3ContentAccessor(contentReference, countingInputStream.getSize());
        } catch (InsufficientDataException | InternalException | InvalidKeyException | IOException |
                 NoSuchAlgorithmException | XmlParserException e) {
            throw new UnwritableContentException(contentReference, e);
        }
    }

    @Override
    public void remove(ContentReference contentReference) throws UnwritableContentException {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(contentReference.getValue())
                    .build())
                    .join();
        } catch (InsufficientDataException | InternalException | InvalidKeyException | IOException |
                 NoSuchAlgorithmException | XmlParserException e) {
            throw new UnwritableContentException(contentReference, e);
        }

    }

}
