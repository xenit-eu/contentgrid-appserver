package com.contentgrid.appserver.actuator.policy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;

public class IsOpaSidecarModeCondition extends NoneNestedConditions {

    public static final String PROPERTY_POLICY_PACKAGE = "contentgrid.system.policyPackage";

    IsOpaSidecarModeCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(PROPERTY_POLICY_PACKAGE)
    static class PolicyPackageIsSet {}
}
