package com.contentgrid.appserver.contentstore.impl.s3;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounds for S3 read acquisition: how often {@link S3ContentStore#getReader} may start a new
 * {@code getObject} operation after a retryable connection-establishment failure, and how long all
 * acquisition work (attempts and backoff delays combined) may take.
 * <p>
 * The retry count bounds <em>SDK operations</em> ({@code S3AsyncClient#getObject} invocations), not
 * individual HTTP attempts: every outer attempt runs through the SDK's own retry policy (and, for
 * large objects, the multipart downloader), so the worst-case number of HTTP requests multiplies.
 * The acquisition timeout is a monotonic deadline shared by attempts and delays; it ends when the
 * response stream is handed to the caller and never constrains the ongoing download.
 */
public record S3ReadRetryPolicy(int maxRetries, Duration acquisitionTimeout, Duration minDelay, Duration maxDelay) {

    /** Default number of additional acquisition attempts after the initial attempt. */
    public static final int DEFAULT_MAX_RETRIES = 1;

    /** Default total budget for read acquisition, covering attempts and backoff delays. */
    public static final Duration DEFAULT_ACQUISITION_TIMEOUT = Duration.ofSeconds(10);

    public static final Duration DEFAULT_MIN_DELAY = Duration.ZERO;

    public static final Duration DEFAULT_MAX_DELAY = Duration.ofMillis(250);

    /** Default policy: one additional attempt within a ten-second acquisition budget. */
    public static final S3ReadRetryPolicy DEFAULT = new S3ReadRetryPolicy(DEFAULT_MAX_RETRIES,
            DEFAULT_ACQUISITION_TIMEOUT);

    public S3ReadRetryPolicy(int maxRetries, Duration acquisitionTimeout) {
        this(maxRetries, acquisitionTimeout, DEFAULT_MIN_DELAY, DEFAULT_MAX_DELAY);
    }

    /**
     * @param maxRetries additional acquisition attempts after the initial attempt; {@code 0} disables
     * outer retries (the initial attempt still runs)
     * @param acquisitionTimeout total acquisition budget; must be strictly positive
     * @param minDelay minimum retry delay; nonnegative and no greater than maxDelay
     * @param maxDelay maximum retry delay; zero together with minDelay means immediate retry,
     * equal positive bounds mean a fixed delay
     */
    public S3ReadRetryPolicy {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
        }
        Objects.requireNonNull(acquisitionTimeout, "acquisitionTimeout must not be null");
        if (acquisitionTimeout.isZero() || acquisitionTimeout.isNegative()) {
            throw new IllegalArgumentException(
                    "acquisitionTimeout must be strictly positive: " + acquisitionTimeout);
        }
        Objects.requireNonNull(minDelay, "minDelay must not be null");
        Objects.requireNonNull(maxDelay, "maxDelay must not be null");
        // Delays are sampled and waited in nanoseconds; conversion also rejects unrepresentable settings.
        if (minDelay.isNegative() || maxDelay.isNegative() || minDelay.toNanos() > maxDelay.toNanos()) {
            throw new IllegalArgumentException("retry delays must satisfy 0 <= minDelay <= maxDelay");
        }
    }
}
