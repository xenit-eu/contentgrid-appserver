package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.RemoveBucketArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reproducer for content upload bug where S3 write failures escape as uncaught
 * {@link CompletionException} instead of being wrapped as {@link UnwritableContentException}.
 * <p>
 * When {@link S3ContentStore#writeContent} fails (e.g. broken pipe to S3), the
 * {@code CompletionException} from {@code CompletableFuture.join()} is not caught.
 * This causes the exception to propagate uncaught through the entire request processing chain,
 * bypassing the content store error handling in ContentUploadAttributeMapper and preventing
 * the database transaction from ever starting. The result is a PUT that appears to succeed
 * (HTTP 200 with default status) but content metadata is never persisted, causing subsequent
 * GET requests to return 404.
 */
@Testcontainers
class S3ContentStoreWriteFailureTest {

    @Container
    private static final S3MockContainer s3MockContainer = S3MockUtils.s3MockContainer();

    private MinioAsyncClient client;

    @BeforeEach
    void setUp() {
        client = MinioAsyncClient.builder()
                .endpoint(s3MockContainer.getHttpEndpoint())
                .credentials("test", "test")
                .build();
    }

    /**
     * When the S3 bucket does not exist, putObject fails and .join() throws CompletionException.
     * S3ContentStore.writeContent() should catch this and wrap it as UnwritableContentException,
     * but currently the CompletionException escapes uncaught.
     */
    @Test
    void writeContent_whenS3OperationFails_shouldThrowUnwritableContentException() {
        var store = new S3ContentStore(client, "non-existent-bucket-" + UUID.randomUUID());

        assertThrows(UnwritableContentException.class, () -> store.writeContent(
                new ByteArrayInputStream("test data".getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * Simulates a broken pipe during content upload by using an InputStream that fails mid-read.
     * This mirrors the production scenario where the connection to S3 breaks during upload.
     * The resulting CompletionException (wrapping an IOException) should be caught and wrapped
     * as UnwritableContentException.
     */
    @Test
    void writeContent_whenInputStreamFailsDuringUpload_shouldThrowUnwritableContentException() throws Exception {
        var bucketName = "test-" + UUID.randomUUID();
        client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build()).join();

        var store = new S3ContentStore(client, bucketName);

        var brokenInputStream = new InputStream() {
            private int bytesRead = 0;

            @Override
            public int read() throws IOException {
                if (bytesRead++ > 100) {
                    throw new IOException("Broken pipe");
                }
                return 'x';
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (bytesRead > 100) {
                    throw new IOException("Broken pipe");
                }
                int toRead = Math.min(len, 50);
                for (int i = 0; i < toRead; i++) {
                    b[off + i] = 'x';
                }
                bytesRead += toRead;
                return toRead;
            }
        };

        assertThrows(UnwritableContentException.class, () -> store.writeContent(brokenInputStream));
    }

    /**
     * Verifies that remove() has the same problem: CompletionException from .join()
     * is not caught when the bucket doesn't exist.
     */
    @Test
    void remove_whenS3OperationFails_shouldThrowUnwritableContentException() throws Exception {
        var bucketName = "test-" + UUID.randomUUID();
        client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build()).join();

        var store = new S3ContentStore(client, bucketName);

        // Write content, then delete the bucket to force the remove to fail
        var accessor = store.writeContent(
                new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)));

        // Remove the bucket's contents and the bucket itself to cause subsequent operations to fail
        store.remove(accessor.getReference());
        client.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build()).join();

        // Now the store references a non-existent bucket, so remove should fail with UnwritableContentException
        assertThrows(UnwritableContentException.class, () -> store.remove(accessor.getReference()));
    }
}
