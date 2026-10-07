package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adobe.testing.s3mock.testcontainers.S3MockContainer;
import com.contentgrid.appserver.autoconfigure.s3.S3ClientFactory;
import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import com.contentgrid.appserver.contentstore.api.range.ResolvedContentRange;
import com.contentgrid.appserver.contentstore.impl.utils.testing.S3MockUtils;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

/**
 * Proves the split read/write wiring end to end against S3Mock: large uploads still transparently use
 * multipart upload through the write client (50 MiB parts), while downloads stream back correctly
 * through the single-request read client, including ranges spanning a part boundary.
 */
@Testcontainers
@Timeout(value = 10, unit = TimeUnit.MINUTES)
class S3MultipartSplitClientTest {

    /** Larger than the 50 MiB multipart part size, so the upload takes the multipart path. */
    private static final int LARGE_SIZE = 55 * 1024 * 1024;

    @Container
    private static final S3MockContainer s3MockContainer = S3MockUtils.s3MockContainer();

    private SdkAsyncHttpClient httpClient;
    private S3AsyncClient readClient;
    private S3AsyncClient writeClient;
    private String bucketName;
    private S3ContentStore store;

    @BeforeEach
    void setUp() {
        httpClient = S3ClientFactory.createSharedHttpClient(0, 1);
        var endpoint = s3MockContainer.getHttpEndpoint();
        readClient = S3ClientFactory.createS3ReadAsyncClient(endpoint, "test", "test", null, true,
                httpClient, false);
        writeClient = S3ClientFactory.createS3WriteAsyncClient(endpoint, "test", "test", null, true,
                httpClient, false);
        bucketName = "test-" + UUID.randomUUID();
        writeClient.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();
        store = new S3ContentStore(readClient, writeClient, bucketName);
    }

    @AfterEach
    void tearDown() {
        readClient.close();
        writeClient.close();
        httpClient.close();
    }

    @Test
    void multipartUploadedLargeContent_streamsBackIdentically() throws Exception {
        var content = new byte[LARGE_SIZE];
        new Random(42).nextBytes(content);

        var reference = store.writeContent(new ByteArrayInputStream(content)).getReference();
        // Multipart-completed objects carry an ETag with a part-count suffix (e.g. "<md5>-2"); a plain
        // single PUT would not. This proves the 55 MiB upload took the multipart path.
        var eTag = writeClient.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(reference.getValue())
                        .build()).join().eTag();
        assertTrue(eTag.contains("-"), "expected a multipart ETag, got: " + eTag);
        assertArrayEquals(content, boundedRead(reference, null));
    }

    @Test
    void rangeAcrossPartBoundary_readsSliceThroughSingleGet() throws Exception {
        var content = new byte[LARGE_SIZE];
        new Random(42).nextBytes(content);

        var reference = store.writeContent(new ByteArrayInputStream(content)).getReference();
        // Range straddling the 50 MiB first-part boundary
        long start = 50L * 1024 * 1024 - 2;
        var range = ContentRangeRequest.createRange(start, start + 5).resolve(content.length);
        var bytes = boundedRead(reference, range);
        assertEquals(content.length, bytes.length);
        assertArrayEquals(Arrays.copyOfRange(content, (int) start, (int) start + 6),
                Arrays.copyOfRange(bytes, (int) start, (int) start + 6));
    }

    private byte[] boundedRead(ContentReference reference, ResolvedContentRange range) {
        // Off the test thread with a hard bound: store.join() ignores interrupts, so the class-level
        // Timeout alone could not release a hung read.
        return CompletableFuture.supplyAsync(() -> {
            try (var stream = store.getReader(reference, range).getContentInputStream()) {
                return stream.readAllBytes();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).orTimeout(8, TimeUnit.MINUTES).join();
    }
}
