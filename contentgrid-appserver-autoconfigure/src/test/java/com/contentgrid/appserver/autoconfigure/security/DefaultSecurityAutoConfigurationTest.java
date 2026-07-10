package com.contentgrid.appserver.autoconfigure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.contentgrid.appserver.autoconfigure.opa.OpaSidecarFeature;
import com.contentgrid.appserver.autoconfigure.opa.OpaSidecarFeatureAutoConfiguration;
import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import com.contentgrid.appserver.autoconfigure.security.authority.ActorConverter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class DefaultSecurityAutoConfigurationTest {

    private static final String USER_ISSUER = "https://tenant.example.com";
    private static final String EXTENSION_ISSUER = "https://extensions.example.com";

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DefaultSecurityAutoConfiguration.class,
                    OpaSidecarFeatureAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class, SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class
            ));

    private static ClaimAccessor userTokenWithActClaim() {
        return () -> Map.of(
                JwtClaimNames.ISS, USER_ISSUER,
                JwtClaimNames.SUB, "user-1",
                "act", Map.of(
                        JwtClaimNames.ISS, EXTENSION_ISSUER,
                        JwtClaimNames.SUB, "extension-1"
                )
        );
    }

    private static ClaimAccessor userTokenWithNestedActClaim() {
        return () -> Map.of(
                JwtClaimNames.ISS, USER_ISSUER,
                JwtClaimNames.SUB, "user-1",
                "act", Map.of(
                        JwtClaimNames.ISS, EXTENSION_ISSUER,
                        JwtClaimNames.SUB, "extension-1",
                        "act", Map.of(
                                JwtClaimNames.ISS, EXTENSION_ISSUER,
                                JwtClaimNames.SUB, "extension-2"
                        )
                )
        );
    }

    @Test
    void hasSingleUserActorConverter() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            assertThat(context).hasSingleBean(ActorConverter.class);
            assertThat(context).hasBean("userActorConverter");
        });
    }

    @Test
    void actClaim_resolvesToExtensionParentActor() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            // The gateway is the only trusted token source: the top-level token is always the USER,
            // and every 'act' entry is positionally classified as an EXTENSION.
            var actor = context.getBean("userActorConverter", ActorConverter.class).convert(userTokenWithActClaim());
            assertThat(actor).isNotNull();
            assertThat(actor.type()).isEqualTo(ActorType.USER);
            assertThat(actor.parent()).isNotNull();
            assertThat(actor.parent().type()).isEqualTo(ActorType.EXTENSION);
            assertThat(actor.parent().parent()).isNull();
        });
    }

    @Test
    void nestedActClaim_resolvesToNestedExtensionParentActors() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            var actor = context.getBean("userActorConverter", ActorConverter.class)
                    .convert(userTokenWithNestedActClaim());
            assertThat(actor).isNotNull();
            assertThat(actor.type()).isEqualTo(ActorType.USER);
            assertThat(actor.parent()).isNotNull();
            assertThat(actor.parent().type()).isEqualTo(ActorType.EXTENSION);
            assertThat(actor.parent().parent()).isNotNull();
            assertThat(actor.parent().parent().type()).isEqualTo(ActorType.EXTENSION);
            assertThat(actor.parent().parent().parent()).isNull();
        });
    }
}
