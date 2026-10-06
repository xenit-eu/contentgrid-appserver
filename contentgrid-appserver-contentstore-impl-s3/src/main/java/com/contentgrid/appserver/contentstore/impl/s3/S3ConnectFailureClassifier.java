package com.contentgrid.appserver.contentstore.impl.s3;

import java.net.ConnectException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Classifies S3 read-acquisition failures as retryable connection-establishment failures.
 * <p>
 * Only a typed {@link ConnectException} (including the Netty {@code ConnectTimeoutException} subtype)
 * is retryable, and only when reached exclusively through recognized async/SDK wrappers:
 * {@link CompletionException}, {@link ExecutionException} and {@link SdkClientException} (which
 * includes the multipart downloader's {@code NonRetryableException} wrapper). Any other wrapper in
 * the chain — arbitrary {@code RuntimeException}s, {@code IOException}s, service exceptions such as
 * {@code S3Exception} — stops the search and marks the failure permanent. No message matching is used.
 * <p>
 * In particular this excludes read/socket timeouts, TLS failures, service/auth/404/range errors,
 * decryption failures and cancellation, interruption or deadline exhaustion.
 */
final class S3ConnectFailureClassifier {

    private S3ConnectFailureClassifier() {
    }

    static boolean isRetryableConnectFailure(Throwable failure) {
        return matchedConnectFailure(failure) != null;
    }

    /**
     * @return simple class name of the matched {@link ConnectException}, or {@code "unknown"} when the
     * failure is not a retryable connection failure
     */
    static String connectFailureCategory(Throwable failure) {
        var matched = matchedConnectFailure(failure);
        return matched != null ? matched.getClass().getSimpleName() : "unknown";
    }

    private static ConnectException matchedConnectFailure(Throwable failure) {
        var current = failure;
        while (current != null) {
            if (current instanceof ConnectException connectException) {
                return connectException;
            }
            if (current instanceof CompletionException
                    || current instanceof ExecutionException
                    || current instanceof SdkClientException) {
                current = current.getCause();
            } else {
                return null;
            }
        }
        return null;
    }
}
