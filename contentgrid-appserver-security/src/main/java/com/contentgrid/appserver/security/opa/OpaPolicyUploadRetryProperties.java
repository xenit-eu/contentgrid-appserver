package com.contentgrid.appserver.security.opa;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Backoff between attempts grows as {@code initialDelay * multiplier^attempt}, capped at {@code maxDelay}.
 * After {@code maxRetries} failed retries, the upload is given up on.
 */
@ConfigurationProperties(prefix = "contentgrid.appserver.opa.policy-upload.retry")
public record OpaPolicyUploadRetryProperties(
        @DefaultValue("100ms") Duration initialDelay,
        @DefaultValue("30s") Duration maxDelay,
        @DefaultValue("2") double multiplier,
        @DefaultValue("5") long maxRetries
) {}
