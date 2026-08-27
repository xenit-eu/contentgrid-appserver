package com.contentgrid.appserver.autoconfigure.actuator;

import static org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties.DEFAULT_FILTER_ORDER;

import com.contentgrid.appserver.actuator.policy.PolicyBundleActuator;
import com.contentgrid.appserver.actuator.policy.PolicyBundleEtagFilter;
import org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link PolicyBundleEtagFilter} in front of the policy bundle endpoint.
 * <p>
 * This is a {@link ManagementContextConfiguration} rather than a regular auto-configuration because a management
 * server running on its own port lives in a child context, which a filter registered in the main context would
 * never reach.
 */
@ManagementContextConfiguration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(PolicyBundleEtagFilter.class)
public class PolicyBundleEtagManagementContextConfiguration {

    /**
     * Runs after the spring security filter chain, so that a {@code 304} is never returned
     * to a caller that security would have rejected.
     */
    private static final int FILTER_ORDER = DEFAULT_FILTER_ORDER + 10;

    @Bean
    FilterRegistrationBean<PolicyBundleEtagFilter> policyBundleEtagFilterRegistration(
            PathMappedEndpoints pathMappedEndpoints) {
        var registration = new FilterRegistrationBean<>(new PolicyBundleEtagFilter());
        registration.setOrder(FILTER_ORDER);

        var path = pathMappedEndpoints.getPath(EndpointId.of(PolicyBundleActuator.ENDPOINT_ID));
        if (path == null) {
            // the endpoint is not exposed over http, so there is nothing to attach to
            registration.setEnabled(false);
            return registration;
        }
        registration.addUrlPatterns(path);
        return registration;
    }
}
