package com.contentgrid.appserver.autoconfigure.opa;

import static com.contentgrid.appserver.actuator.policy.OnPolicyPackageCondition.isCentralizedMode;

import com.contentgrid.appserver.security.opa.OpaHealthIndicator;
import com.contentgrid.appserver.security.opa.OpaStatus;
import com.contentgrid.appserver.security.opa.OpaStatusImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class OpaHealthIndicatorAutoConfiguration {

    @Bean
    public OpaStatusImpl opaStatus(Environment environment) {
        // When in centralized OPA mode, the appserver isn't responsible for OPA, so status UP
        return new OpaStatusImpl(isCentralizedMode(environment) ? Status.UP : Status.DOWN);
    }

    @Bean
    public HealthIndicator opaHealthIndicator(OpaStatus opaStatus) {
        return new OpaHealthIndicator(opaStatus);
    }
}
