package com.contentgrid.appserver.autoconfigure.opa;

import com.contentgrid.appserver.actuator.policy.OnMissingPolicyPackageCondition;
import com.contentgrid.appserver.actuator.policy.OnPolicyPackageCondition;
import com.contentgrid.appserver.security.opa.OpaSidecarFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Registered a bean unconditionally for checking at runtime if the opa sidecar feature is active.
 */
@AutoConfiguration
public class OpaSidecarFeatureAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnMissingPolicyPackageCondition.class)
    public OpaSidecarFeature opaSidecarFeatureActive() {
        return () -> true;
    }

    @Bean
    @ConditionalOnMissingBean
    @Conditional(OnPolicyPackageCondition.class)
    public OpaSidecarFeature opaSidecarFeatureInactive() {
        return () -> false;
    }
}
