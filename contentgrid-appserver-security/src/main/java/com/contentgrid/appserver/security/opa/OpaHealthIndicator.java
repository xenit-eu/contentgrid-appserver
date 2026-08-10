package com.contentgrid.appserver.security.opa;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpaHealthIndicator implements HealthIndicator {

    private final OpaStatus opaStatus;

    @Override
    public Health health() {
        return opaStatus.getHealth();
    }
}
