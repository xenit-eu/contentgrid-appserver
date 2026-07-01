package com.contentgrid.appserver.autoconfigure.security;

import com.contentgrid.appserver.autoconfigure.opa.authorization.AppserverOpaInputProvider;
import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import com.contentgrid.appserver.autoconfigure.security.authority.ActorConverter;
import com.contentgrid.appserver.autoconfigure.security.authority.ClaimUtil;
import com.contentgrid.appserver.autoconfigure.security.authority.UserGrantedAuthorityConverter;
import com.contentgrid.thunx.pdp.opa.OpaInputProvider;
import com.contentgrid.thunx.webmvc.autoconfigure.WebMvcAbacAutoConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.StringUtils;

@AutoConfiguration(before = SecurityAutoConfiguration.class)
@AutoConfigureBefore(WebMvcAbacAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
public class DefaultSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OpaInputProvider<Authentication, HttpServletRequest> appserverOpaInputProvider() {
        return new AppserverOpaInputProvider();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoder,
            ObjectProvider<AuthorizationManager<RequestAuthorizationContext>> policyAuthorizationManager,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}") String userIssuerUri,
            @Value("${contentgrid.security.extension-system.issuer-uri:#{null}}") String extensionIssuerUri
    ) throws Exception {
        var authorizationManager = policyAuthorizationManager.getIfAvailable();
        if (authorizationManager != null) {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().access(authorizationManager));
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        }

        var decoder = jwtDecoder.getIfAvailable();
        if (decoder != null) {
            if (StringUtils.hasText(userIssuerUri) && StringUtils.hasText(extensionIssuerUri)) {
                http.oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(
                        multiIssuerAuthenticationManagerResolver(decoder, userIssuerUri, extensionIssuerUri)));
            } else {
                http.oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter(userActorConverter(extensionIssuerUri)))));
            }
        }
        return http.build();
    }

    /**
     * Extensions authenticate via a shared "extension system" issuer, distinct from this application's own
     * (tenant) user issuer, so the two need separate {@link JwtDecoder}s and are resolved by the {@code iss} claim.
     */
    private static AuthenticationManagerResolver<HttpServletRequest> multiIssuerAuthenticationManagerResolver(
            JwtDecoder userJwtDecoder, String userIssuerUri, String extensionIssuerUri) {
        var userProvider = new JwtAuthenticationProvider(userJwtDecoder);
        userProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter(userActorConverter(extensionIssuerUri)));

        var extensionProvider = new JwtAuthenticationProvider(JwtDecoders.fromIssuerLocation(extensionIssuerUri));
        extensionProvider.setJwtAuthenticationConverter(jwtAuthenticationConverter(extensionActorConverter(extensionIssuerUri)));

        Map<String, AuthenticationManager> managers = Map.of(
                userIssuerUri, userProvider::authenticate,
                extensionIssuerUri, extensionProvider::authenticate
        );
        return new JwtIssuerAuthenticationManagerResolver(managers::get);
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter(ActorConverter actorConverter) {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new UserGrantedAuthorityConverter(actorConverter));
        return converter;
    }

    private static ActorConverter userActorConverter(String extensionIssuerUri) {
        Predicate<String> isUserIssuer = StringUtils.hasText(extensionIssuerUri)
                ? Predicate.not(extensionIssuerUri::equals)
                : issuer -> true;
        return new ActorConverter(isUserIssuer, ActorType.USER, ClaimUtil::userClaims);
    }

    private static ActorConverter extensionActorConverter(String extensionIssuerUri) {
        return new ActorConverter(Predicate.isEqual(extensionIssuerUri), ActorType.EXTENSION, ClaimUtil::extensionSystemClaims);
    }
}
