package com.contentgrid.appserver.security.opa.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.contentgrid.appserver.security.opa.authorization.AuthenticationModel.ActorKind;
import com.contentgrid.appserver.security.opa.authorization.AuthenticationModel.AuthenticationKind;
import com.contentgrid.appserver.security.authority.Actor;
import com.contentgrid.appserver.security.authority.Actor.ActorType;
import com.contentgrid.appserver.security.authority.DelegatedAuthenticationDetailsGrantedAuthority;
import com.contentgrid.appserver.security.authority.PrincipalAuthenticationDetailsGrantedAuthority;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AuthenticationModelTest {

    private static final String USER_ISSUER = "https://authentication.invalid/realms/my-user-realm";
    private static final String EXTENSION_SYSTEM_ISSUER = "https://extensions.invalid/authentication/system";

    @Test
    void fromUserAccessToken() {
        var model = AuthenticationModel.from(new TestingAuthenticationToken(null, null, List.of(
                new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                        ActorType.USER,
                        () -> Map.of(
                                "iss", USER_ISSUER,
                                "sub", "04c2cbec-faad-4dc8-ba6f-edb3d5b902e9",
                                "name", "Alice",
                                "email", "alice@wonderland.example",
                                "contentgrid:custom", List.of("blue", "green")
                        ),
                        null
                ))
        )));

        assertThat(model.isAuthenticated()).isTrue();
        assertThat(model.getKind()).isEqualTo(AuthenticationKind.USER);
        assertThat(model.getPrincipal().kind()).isEqualTo(ActorKind.USER);
        assertThat(model.getPrincipal().claims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "iss", USER_ISSUER,
                "name", "Alice",
                "email", "alice@wonderland.example",
                "sub", "04c2cbec-faad-4dc8-ba6f-edb3d5b902e9",
                "contentgrid:custom", List.of("blue", "green")
        ));

        assertThat(model.getActor()).isNull();
    }

    @Test
    void fromAnonymousAccessToken() {
        var anonymousToken = new AnonymousAuthenticationToken("test", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        var model = AuthenticationModel.from(anonymousToken);

        assertThat(model.isAuthenticated()).isFalse();
        assertThat(model.getKind()).isEqualTo(AuthenticationKind.ANONYMOUS);
        assertThat(model.getPrincipal()).isNull();
        assertThat(model.getActor()).isNull();
    }

    @Test
    void fromServiceAccountAccessToken() {
        var model = AuthenticationModel.from(new TestingAuthenticationToken(null, null, List.of(
                new PrincipalAuthenticationDetailsGrantedAuthority(new Actor(
                        ActorType.EXTENSION,
                        () -> Map.of(
                                "iss", EXTENSION_SYSTEM_ISSUER,
                                "sub", "extension123"
                        ),
                        null
                ))
        )));

        assertThat(model.isAuthenticated()).isTrue();
        assertThat(model.getKind()).isEqualTo(AuthenticationKind.SYSTEM);
        assertThat(model.getPrincipal().kind()).isEqualTo(ActorKind.EXTENSION);
        assertThat(model.getPrincipal().claims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "iss", EXTENSION_SYSTEM_ISSUER,
                "sub", "extension123"
        ));

        assertThat(model.getActor()).isNull();
    }

    @Test
    void fromDelegatedAccountAccessToken() {
        var model = AuthenticationModel.from(new TestingAuthenticationToken(null, null, List.of(
                new DelegatedAuthenticationDetailsGrantedAuthority(
                        new Actor(
                                ActorType.USER,
                                () -> Map.of(
                                        "iss", USER_ISSUER,
                                        "sub", "user",
                                        "contentgrid:claim1", "value1"
                                ),
                                null
                        ),
                        new Actor(
                                ActorType.EXTENSION,
                                () -> Map.of(
                                        "iss", EXTENSION_SYSTEM_ISSUER,
                                        "sub", "extension1"
                                ),
                                null
                        )
                )
        )));

        assertThat(model.isAuthenticated()).isTrue();
        assertThat(model.getKind()).isEqualTo(AuthenticationKind.DELEGATED);

        assertThat(model.getPrincipal().kind()).isEqualTo(ActorKind.USER);
        assertThat(model.getPrincipal().claims()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "iss", USER_ISSUER,
                "sub", "user",
                "contentgrid:claim1", "value1"
        ));

        assertThat(model.getActor().kind()).isEqualTo(ActorKind.EXTENSION);
        assertThat(model.getActor().sub()).isEqualTo("extension1");

    }
}
