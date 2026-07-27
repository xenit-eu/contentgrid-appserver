package com.contentgrid.appserver.security.opa;

/**
 * This interface is used to create a bean for runtime checks. For @Conditional Bean injection using the same check,
 * see {@link com.contentgrid.appserver.actuator.policy.OnPolicyPackageCondition}
 */
public interface OpaSidecarFeature {

    boolean isActive();
}
