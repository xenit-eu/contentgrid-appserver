package com.contentgrid.appserver.contentstore.impl.s3;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.autoconfigure.s3.testing.S3TestClients;
import com.contentgrid.appserver.contentstore.api.UnwritableContentException;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest;

/**
 * Verifies that S3 write failures are wrapped as {@link UnwritableContentException}.
 * <p>
 * When {@link S3ContentStore#writeContent} fails (e.g. broken pipe to S3), the failure must not escape as an
 * unchecked SDK exception. That would propagate uncaught through the entire request processing chain, bypassing
 * the content store error handling in ContentUploadAttributeMapper and preventing the database transaction from
 * ever starting. The result would be a PUT that appears to succeed (HTTP 200 with default status) while content
 * metadata is never persisted, causing subsequent GET requests to return 404.
 */
@Testcontainers
class S3ContentStoreWriteFailureTest {

    @Container
    private static final S3MockContainer s3MockContainer = S3MockUtils.s3MockContainer();

    private S3AsyncClient client;

    @BeforeEach
    void setUp() {
        client = S3TestClients.s3AsyncClient(s3MockContainer.getHttpEndpoint());
    }

    /**
     * When the S3 bucket does not exist, putObject fails with an SDK exception.
     * S3ContentStore.writeContent() should catch this and wrap it as UnwritableContentException.
     */
    @Test
    void writeContent_whenS3OperationFails_shouldThrowUnwritableContentException() {
        var store = new S3ContentStore(client, "non-existent-bucket-" + UUID.randomUUID());

        assertThrows(UnwritableContentException.class, () -> store.writeContent(
                new ByteArrayInputStream("test data".getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * Simulates a broken pipe during content upload by using an InputStream that fails mid-read.
     * This mirrors the production scenario where the connection to the caller breaks during upload.
     * The resulting IOException should be caught and wrapped as UnwritableContentException.
     */
    @Test
    void writeContent_whenInputStreamFailsDuringUpload_shouldThrowUnwritableContentException() {
        var bucketName = "test-" + UUID.randomUUID();
        client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();

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
     * A stream that fails after the first full 50 MiB part forces the failure into the multipart upload path,
     * which must abort the started multipart upload and wrap the failure as UnwritableContentException.
     */
    @Test
    void writeContent_whenInputStreamFailsDuringMultipartUpload_shouldThrowUnwritableContentException() {
        var bucketName = "test-" + UUID.randomUUID();
        client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();

        var store = new S3ContentStore(client, bucketName);

        var failAfter = 51L * 1024 * 1024; // one full part plus a bit, so the failure hits during part 2
        var brokenInputStream = new InputStream() {
            private long bytesRead = 0;

            @Override
            public int read() throws IOException {
                if (bytesRead >= failAfter) {
                    throw new IOException("Broken pipe");
                }
                bytesRead++;
                return 'x';
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (bytesRead >= failAfter) {
                    throw new IOException("Broken pipe");
                }
                int toRead = (int) Math.min(len, failAfter - bytesRead);
                Arrays.fill(b, off, off + toRead, (byte) 'x');
                bytesRead += toRead;
                return toRead;
            }
        };

        assertThrows(UnwritableContentException.class, () -> store.writeContent(brokenInputStream));

        // The failure aborts the multipart upload asynchronously, so no orphaned upload may be left behind
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var uploads = client.listMultipartUploads(
                    ListMultipartUploadsRequest.builder().bucket(bucketName).build()).join();
            assertTrue(uploads.uploads().isEmpty());
        });
    }

    /**
     * Verifies that remove() wraps SDK failures the same way when the bucket doesn't exist.
     */
    @Test
    void remove_whenS3OperationFails_shouldThrowUnwritableContentException() throws Exception {
        var bucketName = "test-" + UUID.randomUUID();
        client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();

        var store = new S3ContentStore(client, bucketName);

        // Write content, then delete the bucket to force the remove to fail
        var accessor = store.writeContent(
                new ByteArrayInputStream("test".getBytes(StandardCharsets.UTF_8)));

        // Remove the bucket's contents and the bucket itself to cause subsequent operations to fail
        store.remove(accessor.getReference());
        client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build()).join();

        // Now the store references a non-existent bucket, so remove should fail with UnwritableContentException
        assertThrows(UnwritableContentException.class, () -> store.remove(accessor.getReference()));
    }
}
