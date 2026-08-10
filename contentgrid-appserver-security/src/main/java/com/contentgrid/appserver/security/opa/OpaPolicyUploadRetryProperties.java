package com.contentgrid.appserver.security.opa;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "contentgrid.appserver.opa.policy-upload.retry")
public record OpaPolicyUploadRetryProperties(
        @DefaultValue("100ms") Duration initialDelay,
        @DefaultValue("30s") Duration maxDelay,
        @DefaultValue("2") double multiplier
) {}
