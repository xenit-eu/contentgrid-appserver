package com.contentgrid.appserver.contentstore.impl.s3;

import com.contentgrid.appserver.contentstore.api.ContentAccessor;
import com.contentgrid.appserver.contentstore.api.ContentReader;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.ContentStore;
import com.contentgrid.appserver.contentstore.api.UnreadableContentException;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.utils.GuardedContentReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

@RequiredArgsConstructor
public class S3ContentStore implements ContentStore {

    @NonNull
    private final S3Client client;

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
     */
    private static final int PART_SIZE = 50 * 1024 * 1024; // 50 MiB

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
        try {
            writeObject(contentReference, inputStream);
            return new S3ContentAccessor(contentReference);
        } catch (SdkException | IOException e) {
            throw new UnwritableContentException(contentReference, e);
        }
    }

    private void writeObject(ContentReference contentReference, InputStream inputStream) throws IOException {
        // readNBytes allocates proportionally to the stream size, so small objects don't cost a full part buffer
        var firstPart = inputStream.readNBytes(PART_SIZE);
        if (firstPart.length < PART_SIZE) {
            client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(contentReference.getValue())
                            .contentType(CONTENT_TYPE)
                            .build(),
                    requestBodyFor(firstPart, firstPart.length));
        } else {
            writeObjectMultipart(contentReference, inputStream, firstPart);
        }
    }

    /**
     * @param buffer completely filled with the first part; reused as read buffer for every following part
     */
    private void writeObjectMultipart(ContentReference contentReference, InputStream inputStream, byte[] buffer)
            throws IOException {
        var uploadId = client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(contentReference.getValue())
                        .contentType(CONTENT_TYPE)
                        .build())
                .uploadId();
        try {
            var completedParts = new ArrayList<CompletedPart>();
            var bytesInBuffer = buffer.length;
            while (bytesInBuffer > 0) {
                var partNumber = completedParts.size() + 1;
                var uploadPartResponse = client.uploadPart(UploadPartRequest.builder()
                                .bucket(bucketName)
                                .key(contentReference.getValue())
                                .uploadId(uploadId)
                                .partNumber(partNumber)
                                .build(),
                        requestBodyFor(buffer, bytesInBuffer));
                completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build());
                if (bytesInBuffer < PART_SIZE) {
                    break; // The stream ended inside this part, so it was the last one
                }
                bytesInBuffer = inputStream.readNBytes(buffer, 0, buffer.length);
            }
            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(contentReference.getValue())
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build())
                    .build());
        } catch (SdkException | IOException e) {
            abortMultipartUpload(contentReference, uploadId, e);
            throw e;
        }
    }

    private void abortMultipartUpload(ContentReference contentReference, String uploadId, Exception cause) {
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(contentReference.getValue())
                    .uploadId(uploadId)
                    .build());
        } catch (SdkException abortFailure) {
            cause.addSuppressed(abortFailure);
        }
    }

    private static RequestBody requestBodyFor(byte[] buffer, int length) {
        return RequestBody.fromInputStream(new ByteArrayInputStream(buffer, 0, length), length);
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
