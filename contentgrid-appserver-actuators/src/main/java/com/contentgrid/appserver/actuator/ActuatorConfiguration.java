package com.contentgrid.appserver.actuator;

import com.contentgrid.appserver.actuator.policy.PolicyActuator;
import com.contentgrid.appserver.actuator.policy.PolicyVariables;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.EndpointRequestMatcher;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class ActuatorConfiguration {

    @Autowired
    ApplicationContext applicationContext;

    @Configuration
    class PolicyActuatorConfiguration {
        @Bean
        PolicyVariables policyVariables(ContentgridApplicationProperties applicationProperties) {
            return PolicyVariables.builder()
                    .policyPackageName(applicationProperties.getSystem().getPolicyPackage())
                    .build();
        }

        @Bean
        PolicyActuator policyActuator(PolicyVariables policyVariables) {
            return new PolicyActuator(applicationContext.getResource("classpath:rego/policy.rego"), policyVariables);
        }

    }

    @Bean
    @ConfigurationProperties(prefix = "contentgrid")
    ContentgridApplicationProperties contentgridApplicationProperties() {
        return new ContentgridApplicationProperties();
    }

    @Configuration
    class ActuatorEndpointsWebSecurityConfiguration {
        /**
         * List of publicly accessible management endpoints
         */
        private static final EndpointRequestMatcher PUBLIC_ENDPOINTS = EndpointRequest.to(
                InfoEndpoint.class,
                HealthEndpoint.class
        );

        /**
         * List of management metrics endpoints, allowed when the management port and server port are different.
         */
        private static final EndpointRequestMatcher METRICS_ENDPOINTS = EndpointRequest.to(
                MetricsEndpoint.class,
                PrometheusScrapeEndpoint.class
        );

        @Bean
        SecurityFilterChain actuatorEndpointsSecurityFilterChain(HttpSecurity http, Environment environment) throws Exception {

            http
                    .securityMatcher(EndpointRequest.toAnyEndpoint())
                    .authorizeHttpRequests((requests) -> requests.requestMatchers(
                            PUBLIC_ENDPOINTS,
                            new AndRequestMatcher(
                                    METRICS_ENDPOINTS,
                                    request -> ManagementPortType.get(environment) == ManagementPortType.DIFFERENT
                            ),
                            new AndRequestMatcher(
                                    EndpointRequest.toAnyEndpoint(),
                                    new LoopbackInetAddressMatcher()
                            ),
                            new AndRequestMatcher(
                                    EndpointRequest.to(PolicyActuator.class),
                                    request -> ManagementPortType.get(environment) == ManagementPortType.DIFFERENT
                            )).permitAll());

            // all the other /actuator endpoints fall through
            return http.build();
        }

        private static class LoopbackInetAddressMatcher implements RequestMatcher {

            @Override
            public boolean matches(HttpServletRequest request) {
                return isLoopbackAddress(request.getRemoteAddr());
            }

            boolean isLoopbackAddress(String address) {
                try {
                    var remoteAddress = InetAddress.getByName(address);
                    return remoteAddress.isLoopbackAddress();
                } catch (UnknownHostException ex) {
                    return false;
                }
            }
        }

    }
}
