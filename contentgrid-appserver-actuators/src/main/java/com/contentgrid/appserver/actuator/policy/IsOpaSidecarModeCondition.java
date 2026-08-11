package com.contentgrid.appserver.actuator.policy;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when {@code contentgrid.system.policyPackage} is blank/unset. In that case, the app server has an OPA
 * sidecar rather which is has to contact for authentication.
 * When the policyPackage is set, the gateway contacts a centralised OPA.
 * <p>
 * See also {@code com.contentgrid.appserver.autoconfigure.opa.OpaSidecarAbacSourceEnvironmentPostProcessor} which
 * gates the other OPA sidecar branches.
 */
public class IsOpaSidecarModeCondition extends SpringBootCondition {

    public static final String PROPERTY_POLICY_PACKAGE = "contentgrid.system.policyPackage";

    public static boolean isOpaSidecarMode(Environment environment) {
        return !StringUtils.hasText(environment.getProperty(PROPERTY_POLICY_PACKAGE));
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean sidecarMode = isOpaSidecarMode(context.getEnvironment());
        return new ConditionOutcome(sidecarMode,
                PROPERTY_POLICY_PACKAGE + " " + (sidecarMode ? "is not set" : "is set"));
    }
}
