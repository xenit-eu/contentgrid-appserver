package com.contentgrid.appserver.security.opa;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.util.StringUtils;

public class OpaHealthIndicator implements HealthIndicator {

    // volatile is sufficient here, because the health object itself is never mutated.
    @SuppressWarnings("java:S3077")
    private volatile Health health;

    public OpaHealthIndicator() {
        setHealth(Status.DOWN, "message", "Initial status.");
    }

    @Override
    public Health health() {
        return health;
    }

    public void setUp() {
        setHealth(Status.UP, null, null);
    }

    public void setDown(String key, Object value) {
        setHealth(Status.DOWN, key, value);
    }

    public void setHealth(Status status, String key, Object value) {
        Health.Builder builder = Health.status(status);
        if (StringUtils.hasText(key) && value != null) {
            builder.withDetail(key, value);
        }
        this.health = builder.build();
    }
}
