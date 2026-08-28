package com.contentgrid.appserver.autoconfigure.security;

import com.contentgrid.appserver.actuator.policy.IsOpaSidecarModeCondition;
import com.contentgrid.appserver.security.authority.GatewayJwtAuthenticationDetailsConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Slf4j
@AutoConfiguration(before = SecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
public class DefaultSecurityAutoConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<AuthorizationManager<RequestAuthorizationContext>> policyAuthorizationManagerProvider,
            ObjectProvider<JwtDecoder> jwtDecoderProvider
    ) {
        var authorizationManager = policyAuthorizationManagerProvider.getIfAvailable(AuthenticatedAuthorizationManager::authenticated);
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(authorizationManager));
        jwtDecoderProvider.ifAvailable(jwtDecoder -> {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        });
        return http.build();
    }

    @ConditionalOnProperty(value = "contentgrid.thunx.abac.source", havingValue = "opa")
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new GatewayJwtAuthenticationDetailsConverter());
        return converter;
    }

    /**
     * Fails application startup outright if thunx is configured to authorize through OPA while
     * {@code contentgrid.system.policyPackage} is also set (i.e. centralized/Solon mode).
     * In the centralized OPA mode, the policy is collected by Solon and served from a central OPA,
     * so it would be inconsistent to authorize through a sidecar OPA.
     */
    @Bean
    @ConditionalOnProperty(IsOpaSidecarModeCondition.PROPERTY_POLICY_PACKAGE)
    @ConditionalOnProperty(value = "contentgrid.thunx.abac.source", havingValue = "opa")
    InitializingBean policyAuthorizationManagerConflictValidator() {
        return () -> {
            throw new IllegalStateException(
                    "The property 'contentgrid.thunx.abac.source' is set to 'opa', meaning authorization requires an OPA"
                            + "sidecar. But contentgrid.system.policyPackage is also set, which puts "
                            + "this app in centralized OPA mode. These are mutually exclusive: "
                            + "unset contentgrid.system.policyPackage for sidecar mode, "
                            + "or set contentgrid.thunx.abac.source to a different option.");
        };
    }

}
