package com.contentgrid.appserver.contentstore.impl.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class S3ReadRetryPolicyTest {

    @Test
    void defaults_matchApprovedContract() {
        assertEquals(1, S3ReadRetryPolicy.DEFAULT.maxRetries());
        assertEquals(Duration.ofSeconds(10), S3ReadRetryPolicy.DEFAULT.acquisitionTimeout());
    }

    @Test
    void zeroRetries_disablesOuterRetriesOnly() {
        var policy = new S3ReadRetryPolicy(0, Duration.ofSeconds(10));
        assertEquals(0, policy.maxRetries());
    }

    @Test
    void negativeRetries_rejected() {
        var timeout = Duration.ofSeconds(10);
        assertThrows(IllegalArgumentException.class, () -> new S3ReadRetryPolicy(-1, timeout));
    }

    @Test
    void zeroTimeout_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new S3ReadRetryPolicy(1, Duration.ZERO));
    }

    @Test
    void negativeTimeout_rejected() {
        var timeout = Duration.ofSeconds(-1);
        assertThrows(IllegalArgumentException.class, () -> new S3ReadRetryPolicy(1, timeout));
    }

    @Test
    void nullTimeout_rejected() {
        assertThrows(NullPointerException.class, () -> new S3ReadRetryPolicy(1, null));
    }

    @Test
    void backwardsCompatibleConstructor_usesDefaultDelayBounds() {
        var policy = new S3ReadRetryPolicy(1, Duration.ofSeconds(10));
        assertEquals(Duration.ZERO, policy.minDelay());
        assertEquals(Duration.ofMillis(250), policy.maxDelay());
    }

    @Test
    void zeroDelayBounds_allowImmediateRetry() {
        var policy = new S3ReadRetryPolicy(1, Duration.ofSeconds(10), Duration.ZERO, Duration.ZERO);
        assertEquals(Duration.ZERO, policy.minDelay());
        assertEquals(Duration.ZERO, policy.maxDelay());
    }

    @Test
    void nullDelay_rejected() {
        var timeout = Duration.ofSeconds(10);
        var maxDelay = Duration.ofMillis(250);
        assertThrows(NullPointerException.class,
                () -> new S3ReadRetryPolicy(1, timeout, null, maxDelay));
        assertThrows(NullPointerException.class,
                () -> new S3ReadRetryPolicy(1, timeout, Duration.ZERO, null));
    }

    @Test
    void negativeOrReversedDelayBounds_rejected() {
        var timeout = Duration.ofSeconds(10);
        var negativeDelay = Duration.ofMillis(-1);
        var maxDelay = Duration.ofMillis(250);
        var reversedMinDelay = Duration.ofMillis(300);
        assertThrows(IllegalArgumentException.class, () -> new S3ReadRetryPolicy(1, timeout,
                negativeDelay, maxDelay));
        assertThrows(IllegalArgumentException.class, () -> new S3ReadRetryPolicy(1, timeout,
                Duration.ZERO, negativeDelay));
        assertThrows(IllegalArgumentException.class, () -> new S3ReadRetryPolicy(1, timeout,
                reversedMinDelay, maxDelay));
    }

    @Test
    void unrepresentableDelay_rejectedAtConstruction() {
        var timeout = Duration.ofSeconds(10);
        var unrepresentableDelay = Duration.ofSeconds(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> new S3ReadRetryPolicy(1, timeout,
                Duration.ZERO, unrepresentableDelay));
    }
}
