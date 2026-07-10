package com.contentgrid.appserver.actuator.policy;

import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.context.annotation.Conditional;

/**
 * The inverse of {@link OnPolicyPackageCondition}: matches when {@code contentgrid.system.policyPackage} is
 * blank/unset. In that case, the appserver has an OPA sidecar.
 */
public class OnMissingPolicyPackageCondition extends NoneNestedConditions {

    OnMissingPolicyPackageCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @Conditional(OnPolicyPackageCondition.class)
    static class PolicyPackageIsSet {

    }
}
