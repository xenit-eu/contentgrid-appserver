package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.contentgrid.appserver.contentstore.api.ContentReference;
import com.contentgrid.appserver.contentstore.api.range.ContentRangeRequest;
import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Verifies that {@link S3ContentStore} routes each operation to the intended S3 client: downloads go
 * through the read client as ordinary (non-multipart) requests, uploads and deletes through the write
 * client, and the legacy single-client constructor keeps its externally chosen behavior for all three.
 */
class S3ContentStoreClientRoutingTest {

    private static final String BUCKET = "bucket";
    private static final byte[] CONTENT = "0123456789".getBytes(StandardCharsets.UTF_8);

    /** Fake S3 client recording the requests it receives, answering GETs from in-memory content. */
    private static final class RecordingClient implements InvocationHandler {
        private final List<Object> requests = new ArrayList<>();
        private final byte[] content;

        private RecordingClient(byte[] content) {
            this.content = content;
        }

        static RecordingClient create(byte[] content) {
            return new RecordingClient(content);
        }

        S3AsyncClient client() {
            return (S3AsyncClient) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {S3AsyncClient.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getObject" -> answerGet(toGetObjectRequest(args[0]));
                case "putObject" -> {
                    requests.add(args[0]);
                    yield CompletableFuture.completedFuture(PutObjectResponse.builder().build());
                }
                case "deleteObject" -> {
                    requests.add(args[0]);
                    yield CompletableFuture.completedFuture(DeleteObjectResponse.builder().build());
                }
                case "close" -> null;
                case "toString" -> "RecordingClient";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        @SuppressWarnings("unchecked")
        private GetObjectRequest toGetObjectRequest(Object firstArg) {
            if (firstArg instanceof GetObjectRequest request) {
                requests.add(request);
                return request;
            }
            if (firstArg instanceof Consumer<?> consumer) {
                var builder = GetObjectRequest.builder();
                ((Consumer<GetObjectRequest.Builder>) consumer).accept(builder);
                var request = builder.build();
                requests.add(request);
                return request;
            }
            throw new UnsupportedOperationException("getObject(" + firstArg + ")");
        }

        private CompletableFuture<ResponseInputStream<GetObjectResponse>> answerGet(GetObjectRequest request) {
            byte[] body = content;
            var response = GetObjectResponse.builder().contentLength((long) body.length);
            if (request.range() != null) {
                var range = request.range().substring("bytes=".length()).split("-", 2);
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                body = java.util.Arrays.copyOfRange(content, start, end + 1);
                response.contentLength((long) body.length)
                        .contentRange("bytes %d-%d/%d".formatted(start, end, content.length));
            }
            return CompletableFuture.completedFuture(
                    new ResponseInputStream<>(response.build(), new ByteArrayInputStream(body)));
        }
    }

    @Test
    void getReader_withoutRange_usesReadClientWithPlainGet() throws Exception {
        var read = RecordingClient.create(CONTENT);
        var write = RecordingClient.create(CONTENT);
        var store = new S3ContentStore(read.client(), write.client(), BUCKET);

        var reference = ContentReference.of("object-key");
        try (var stream = store.getReader(reference, null).getContentInputStream()) {
            assertArrayEquals(CONTENT, stream.readAllBytes());
        }

        assertTrue(write.requests.isEmpty(), "writes must not receive downloads, got: " + write.requests);
        assertEquals(1, read.requests.size());
        var request = (GetObjectRequest) read.requests.get(0);
        assertEquals(BUCKET, request.bucket());
        assertEquals("object-key", request.key());
        assertNull(request.range(), "full download must not send a Range header");
        assertNull(request.partNumber(), "full download must be a single GET, not a part download");
    }

    @Test
    void getReader_withRange_usesReadClientWithRangeHeader() throws Exception {
        var read = RecordingClient.create(CONTENT);
        var write = RecordingClient.create(CONTENT);
        var store = new S3ContentStore(read.client(), write.client(), BUCKET);

        var range = ContentRangeRequest.createRange(2, 5).resolve(CONTENT.length);
        try (var stream = store.getReader(ContentReference.of("object-key"), range).getContentInputStream()) {
            // S3ContentReader presents the full resource, zero-filling bytes outside the range
            var bytes = stream.readAllBytes();
            assertEquals(CONTENT.length, bytes.length);
            assertArrayEquals("2345".getBytes(StandardCharsets.UTF_8),
                    java.util.Arrays.copyOfRange(bytes, 2, 6));
        }

        assertTrue(write.requests.isEmpty(), "writes must not receive downloads, got: " + write.requests);
        assertEquals(1, read.requests.size());
        var request = (GetObjectRequest) read.requests.get(0);
        assertEquals("bytes=2-5", request.range());
        assertNull(request.partNumber());
    }

    @Test
    void writeContent_usesWriteClient() throws Exception {
        var read = RecordingClient.create(CONTENT);
        var write = RecordingClient.create(CONTENT);
        var store = new S3ContentStore(read.client(), write.client(), BUCKET);

        var accessor = store.writeContent(new ByteArrayInputStream(CONTENT));

        assertTrue(read.requests.isEmpty(), "reads must not receive uploads, got: " + read.requests);
        assertEquals(1, write.requests.size());
        var request = (PutObjectRequest) write.requests.get(0);
        assertEquals(BUCKET, request.bucket());
        assertEquals(accessor.getReference().getValue(), request.key());
    }

    @Test
    void remove_usesWriteClient() throws Exception {
        var read = RecordingClient.create(CONTENT);
        var write = RecordingClient.create(CONTENT);
        var store = new S3ContentStore(read.client(), write.client(), BUCKET);

        var reference = ContentReference.of("object-key");
        store.remove(reference);

        assertTrue(read.requests.isEmpty(), "reads must not receive deletes, got: " + read.requests);
        assertEquals(1, write.requests.size());
        var request = (DeleteObjectRequest) write.requests.get(0);
        assertEquals(BUCKET, request.bucket());
        assertEquals("object-key", request.key());
    }

    @Test
    void legacyConstructor_routesAllOperationsToSingleClient() throws Exception {
        var single = RecordingClient.create(CONTENT);
        var store = new S3ContentStore(single.client(), BUCKET);

        var reference = ContentReference.of("object-key");
        try (var stream = store.getReader(reference, null).getContentInputStream()) {
            stream.readAllBytes();
        }
        store.writeContent(new ByteArrayInputStream(CONTENT));
        store.remove(reference);

        assertEquals(3, single.requests.size());
        assertTrue(single.requests.get(0) instanceof GetObjectRequest);
        assertTrue(single.requests.get(1) instanceof PutObjectRequest);
        assertTrue(single.requests.get(2) instanceof DeleteObjectRequest);
    }
}
