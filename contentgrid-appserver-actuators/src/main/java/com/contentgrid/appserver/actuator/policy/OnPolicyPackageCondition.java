package com.contentgrid.appserver.actuator.policy;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when {@code contentgrid.system.policyPackage} has text. In that case, the app server exposes its policy via
 * {@code /actuator/policy} for centralized (Solon) pickup rather than running in OPA sidecar mode.
 * <p>
 * {@link OnMissingPolicyPackageCondition} is the inverse of this same check, for the OPA sidecar-upload path
 * <p>
 * See also {@code com.contentgrid.appserver.autoconfigure.opa.OpaSidecarAbacSourceEnvironmentPostProcessor} which
 * gates the other OPA sidecar branches.
 */
public class OnPolicyPackageCondition extends SpringBootCondition {

    public static final String PROPERTY_POLICY_PACKAGE = "contentgrid.system.policyPackage";

    public static boolean isCentralizedMode(Environment environment) {
        return StringUtils.hasText(environment.getProperty(PROPERTY_POLICY_PACKAGE));
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean centralizedMode = isCentralizedMode(context.getEnvironment());
        return new ConditionOutcome(centralizedMode, PROPERTY_POLICY_PACKAGE + " " + (centralizedMode ? "is set" : "is not set"));
    }
}
