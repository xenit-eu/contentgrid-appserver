package com.contentgrid.appserver.autoconfigure.s3;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.NonNull;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * Managed S3 clients for content storage: a read client with multipart operation disabled (ordinary
 * single-request downloads) and a write client with multipart operation enabled (transparent multipart
 * uploads), sharing one HTTP transport and connection pool.
 * <p>
 * The holder owns all three resources: {@link #close()} closes the S3 clients first and the shared
 * transport last, exactly once, and is a safe no-op afterwards. S3 clients built by the SDK do not take
 * ownership of a caller-supplied transport, so only this holder may close the shared transport.
 */
public final class S3ContentStoreClients implements AutoCloseable {

    @NonNull
    private final S3AsyncClient readClient;

    @NonNull
    private final S3AsyncClient writeClient;

    @NonNull
    private final SdkAsyncHttpClient httpClient;

    private final AtomicBoolean closed = new AtomicBoolean();

    public S3ContentStoreClients(@NonNull S3AsyncClient readClient, @NonNull S3AsyncClient writeClient,
            @NonNull SdkAsyncHttpClient httpClient) {
        this.readClient = readClient;
        this.writeClient = writeClient;
        this.httpClient = httpClient;
    }

    public S3AsyncClient readClient() {
        return readClient;
    }

    public S3AsyncClient writeClient() {
        return writeClient;
    }

    public SdkAsyncHttpClient httpClient() {
        return httpClient;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                writeClient.close();
            } finally {
                try {
                    readClient.close();
                } finally {
                    httpClient.close();
                }
            }
        }
    }
}
