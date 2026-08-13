package com.contentgrid.appserver.actuator.policy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.NoneNestedConditions;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Matches when {@code contentgrid.system.policyPackage} is blank/unset. In that case, the app server has an OPA
 * sidecar rather which is has to contact for authentication.
 * When the policyPackage is set, the gateway contacts a centralised OPA.
 * <p>
 * See also {@code com.contentgrid.appserver.autoconfigure.opa.OpaSidecarAbacSourceEnvironmentPostProcessor} which
 * gates the other OPA sidecar branches.
 */
public class IsOpaSidecarModeCondition extends NoneNestedConditions {

    public static final String PROPERTY_POLICY_PACKAGE = "contentgrid.system.policyPackage";

    public static boolean isOpaSidecarMode(Environment environment) {
        return !StringUtils.hasText(environment.getProperty(PROPERTY_POLICY_PACKAGE));
    }

    IsOpaSidecarModeCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(PROPERTY_POLICY_PACKAGE)
    static class PolicyPackageIsSet {}
}
