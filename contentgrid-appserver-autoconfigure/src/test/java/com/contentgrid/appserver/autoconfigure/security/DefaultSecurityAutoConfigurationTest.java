package com.contentgrid.appserver.autoconfigure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.security.opa.OpaSidecarFeature;
import com.contentgrid.appserver.autoconfigure.opa.OpaSidecarFeatureAutoConfiguration;
import com.contentgrid.appserver.security.authority.AuthenticationDetails;
import com.contentgrid.appserver.security.authority.GatewayAuthClaimNames;
import jakarta.servlet.Filter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.BeanIds;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

class DefaultSecurityAutoConfigurationTest {

    private static final String USER_ISSUER = "https://tenant.example.com";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DefaultSecurityAutoConfiguration.class,
                    OpaSidecarFeatureAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class, SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class
            ));

    @Test
    void hasSingleSecurityFilterChain() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
    }

    // FR4: the GatewayJwtAuthenticationDetailsConverter must only be wired in sidecar mode; non-sidecar
    // (centralized/policy-package) mode must keep the previous default (Customizer.withDefaults()) behaviour.
    // These tests drive a gateway-shaped bearer token through the *actual* assembled security filter chain
    // (springSecurityFilterChain bean), without starting a servlet container or DispatcherServlet.

    private static Jwt gatewayShapedJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(JwtClaimNames.ISS, USER_ISSUER)
                .claim(JwtClaimNames.SUB, "user-1")
                .claim(GatewayAuthClaimNames.AUTH_KIND, GatewayAuthClaimNames.AUTH_KIND_USER)
                .claim(GatewayAuthClaimNames.AUTH_PRINCIPAL, Map.of(
                        GatewayAuthClaimNames.KIND, GatewayAuthClaimNames.KIND_USER,
                        JwtClaimNames.SUB, "user-1"
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    private static Jwt contractViolatingJwt() {
        // Gateway-signed but missing the contentgrid:auth:principal claim: a contract violation that must be
        // rejected as an invalid token (401), not classified leniently or crash with a 500.
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(JwtClaimNames.ISS, USER_ISSUER)
                .claim(JwtClaimNames.SUB, "user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void sidecarMode_wiresGatewayJwtAuthenticationDetailsConverter() throws Exception {
        contextRunner
                .withBean(JwtDecoder.class, () -> (JwtDecoder) token -> gatewayShapedJwt())
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var authorities = authenticateWithBearerToken(context).getAuthorities();
                    assertThat(authorities).hasAtLeastOneElementOfType(AuthenticationDetails.class);
                });
    }

    @Test
    void sidecarMode_rejectsContractViolatingToken_asUnauthorized() throws Exception {
        contextRunner
                .withBean(JwtDecoder.class, () -> (JwtDecoder) token -> contractViolatingJwt())
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var response = filterBearerTokenRequest(context, new AtomicReference<>());
                    assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
                    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
                });
    }

    @Test
    void nonSidecarMode_doesNotWireGatewayJwtAuthenticationDetailsConverter() throws Exception {
        contextRunner
                // OpaSidecarFeatureAutoConfiguration derives this from contentgrid.system.policyPackage in a real
                // deployment; overridden directly here (instead of via that property) because
                // OnPolicyPackageCondition's PARSE_CONFIGURATION-phase evaluation isn't reliably exercised by a
                // bare ApplicationContextRunner. FR4 is about DefaultSecurityAutoConfiguration's behaviour given
                // an OpaSidecarFeature, not about how that bean itself gets computed - that's already covered
                // elsewhere (see OnPolicyPackageCondition / OnMissingPolicyPackageCondition).
                .withBean("opaSidecarFeatureOverride", OpaSidecarFeature.class, () -> () -> false)
                .withBean(JwtDecoder.class, () -> (JwtDecoder) token -> gatewayShapedJwt())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OpaSidecarFeature.class).isActive()).isFalse();

                    // Customizer.withDefaults() has no idea about contentgrid:auth:* claims, so none of the
                    // resulting authorities are AuthenticationDetails - this is what pins the "previous default
                    // behaviour" requirement, not just "no exception was thrown".
                    var authorities = authenticateWithBearerToken(context).getAuthorities();
                    assertThat(authorities).noneMatch(AuthenticationDetails.class::isInstance);
                });
    }

    /**
     * Pushes a bearer-token request through the real, fully assembled {@code springSecurityFilterChain} bean
     * (the {@link org.springframework.security.web.FilterChainProxy} that {@code @EnableWebSecurity} registers),
     * and returns the {@link Authentication} left in the {@link SecurityContextHolder} once the request reaches
     * the terminal filter. No servlet container or DispatcherServlet is started.
     */
    private static Authentication authenticateWithBearerToken(ApplicationContext context) throws Exception {
        var captured = new AtomicReference<Authentication>();
        filterBearerTokenRequest(context, captured);

        assertThat(captured.get())
                .as("request should have reached the terminal filter (i.e. authenticated and authorized)")
                .isNotNull();
        return captured.get();
    }

    private static MockHttpServletResponse filterBearerTokenRequest(ApplicationContext context,
            AtomicReference<Authentication> captured) throws Exception {
        var filter = context.getBean(BeanIds.SPRING_SECURITY_FILTER_CHAIN, Filter.class);
        var request = new MockHttpServletRequest("GET", "/");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> captured.set(SecurityContextHolder.getContext().getAuthentication()));
        return response;
    }
}
