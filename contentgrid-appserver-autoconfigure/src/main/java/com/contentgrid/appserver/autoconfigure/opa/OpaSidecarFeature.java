package com.contentgrid.appserver.autoconfigure.opa;

/**
 * Whether this deployment is in "OPA sidecar" mode: the app owns and uploads its own rego policy directly to
 * the OPA at {@code opa.service.url}, rather than exposing it via {@code /actuator/policy} for centralized
 * (Solon) pickup.
 * <p>
 * This interface is used to create a bean for runtime checks. For @Conditional Bean injection using the same check,
 * see {@link com.contentgrid.appserver.actuator.policy.OnPolicyPackageCondition}
 */
public interface OpaSidecarFeature {

    boolean isActive();
}
