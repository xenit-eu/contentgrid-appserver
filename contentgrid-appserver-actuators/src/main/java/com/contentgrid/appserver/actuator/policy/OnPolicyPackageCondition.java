package com.contentgrid.appserver.actuator.policy;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when {@code contentgrid.system.policyPackage} has text. In that case, the app server exposes its policy via
 * {@code /actuator/policy} for centralized (Solon) pickup rather than running in OPA sidecar mode.
 * <p>
 * {@link OnMissingPolicyPackageCondition} is the inverse of this same check, for the OPA sidecar-upload path.
 */
public class OnPolicyPackageCondition extends SpringBootCondition {

    static final String PROPERTY = "contentgrid.system.policyPackage";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var policyPackage = context.getEnvironment().getProperty(PROPERTY);
        boolean set = StringUtils.hasText(policyPackage);
        return new ConditionOutcome(set, PROPERTY + " " + (set ? "is set" : "is not set"));
    }
}
