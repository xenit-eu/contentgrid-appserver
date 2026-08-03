package com.contentgrid.appserver.autoconfigure.opa;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * The opa-sidecar mode is feature flagged by {@code contentgrid.system.policyPackage} being blank. This feature flag
 * is the single source of truth for the opa mode and is set by captain and checked by the gateway.
 * The contentgrid.thunx.abac.source is set with a default value matching the opa mode, but as the last source.
 * So you can still overwrite the property during tests.
 */
public class OpaSidecarAbacSourceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String POLICY_PACKAGE_PROPERTY = "contentgrid.system.policyPackage";
    private static final String ABAC_SOURCE_PROPERTY = "contentgrid.thunx.abac.source";
    private static final String SOURCE_NONE = "none";
    private static final String SOURCE_OPA = "opa";
    private static final String PROPERTY_SOURCE_NAME = "contentgrid-opa-sidecar-abac-source-defaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var defaultSource = StringUtils.hasText(environment.getProperty(POLICY_PACKAGE_PROPERTY))
                ? SOURCE_NONE
                : SOURCE_OPA;
        environment.getPropertySources()
                .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(ABAC_SOURCE_PROPERTY, defaultSource)));
    }
}
