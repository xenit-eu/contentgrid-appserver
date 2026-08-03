package com.contentgrid.appserver.autoconfigure.security;

import com.contentgrid.appserver.actuator.policy.OnPolicyPackageCondition;
import com.contentgrid.appserver.security.authority.GatewayJwtAuthenticationDetailsConverter;
import com.contentgrid.thunx.webmvc.autoconfigure.WebMvcAbacAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Slf4j
@AutoConfiguration(before = SecurityAutoConfiguration.class, after = WebMvcAbacAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableWebSecurity
public class DefaultSecurityAutoConfiguration {

    @Bean
    @ConditionalOnBean(AuthorizationManager.class)
    SecurityFilterChain opaSecurityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoder,
            AuthorizationManager<RequestAuthorizationContext> policyAuthorizationManager
    ) {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(policyAuthorizationManager));
        if (jwtDecoder.getIfAvailable() != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        } else {
            log.warn("No jwtDecoder found, requests will be done anonymously.");
        }
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain centralizedOpaSecurityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoder) {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());

        if (jwtDecoder.getIfAvailable() != null) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }

    /**
     * Fails application startup outright if a policy {@link AuthorizationManager} bean exists (from
     * {@code contentgrid.thunx.abac.source=opa} / {@code opa.service.url}) while {@code contentgrid.system.policyPackage}
     * is also set, i.e. centralized/Solon mode. That combination means the app would silently authorize every
     * request with {@code authenticated()} only, ignoring the configured OPA policy manager, which is never what's
     * intended.
     * <p>
     * Gated by {@code @Conditional}/{@code @ConditionalOnBean} rather than an injected runtime check: this bean, and
     * therefore its failure, only exists when both conditions already hold.
     */
    @Bean
    @Conditional(OnPolicyPackageCondition.class)
    @ConditionalOnBean(AuthorizationManager.class)
    InitializingBean policyAuthorizationManagerConflictValidator() {
        return () -> {
            throw new IllegalStateException(
                    "A policy AuthorizationManager bean is present (from contentgrid.thunx.abac.source=opa / "
                            + "opa.service.url), but contentgrid.system.policyPackage is also set, which puts "
                            + "this app in centralized OPA mode. These are mutually exclusive: "
                            + "unset contentgrid.system.policyPackage for sidecar mode, "
                            + "or remove the OPA client configuration for centralized mode.");
        };
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new GatewayJwtAuthenticationDetailsConverter());
        return converter;
    }
}
