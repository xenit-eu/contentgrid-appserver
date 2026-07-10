package com.contentgrid.appserver.autoconfigure.opa.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.contentgrid.appserver.autoconfigure.opa.authorization.AuthenticationModel.ActorKind;
import com.contentgrid.appserver.autoconfigure.opa.authorization.AuthenticationModel.AuthenticationKind;
import com.contentgrid.appserver.autoconfigure.security.authority.Actor;
import com.contentgrid.appserver.autoconfigure.security.authority.Actor.ActorType;
import com.contentgrid.appserver.autoconfigure.security.authority.DelegatedAuthenticationDetailsGrantedAuthority;
import com.contentgrid.appserver.autoconfigure.security.authority.PrincipalAuthenticationDetailsGrantedAuthority;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimNames;

@ExtendWith(MockitoExtension.class)
class AuthenticationModelTest {

    @Mock
    private Authentication authentication;

    @Test
    void plainUser_yieldsUserKindWithoutActor() {
        var principal = new Actor(ActorType.USER, Map.of(JwtClaimNames.SUB, "user-1"));
        doReturn(List.of(new PrincipalAuthenticationDetailsGrantedAuthority(principal)))
                .when(authentication).getAuthorities();

        var model = AuthenticationModel.from(authentication);

        assertThat(model.getKind()).isEqualTo(AuthenticationKind.USER);
        assertThat(model.getPrincipal().getKind()).isEqualTo(ActorKind.USER);
        assertThat(model.getPrincipal().getClaims()).containsEntry(JwtClaimNames.SUB, "user-1");
        assertThat(model.getActor()).isNull();
    }

    @Test
    void delegatedActor_yieldsDelegatedKindWithPrincipalAndActor() {
        var principal = new Actor(ActorType.USER, Map.of(JwtClaimNames.SUB, "user-1"));
        var actor = new Actor(ActorType.EXTENSION, Map.of(JwtClaimNames.SUB, "extension-1"));
        doReturn(List.of(new DelegatedAuthenticationDetailsGrantedAuthority(principal, actor)))
                .when(authentication).getAuthorities();

        var model = AuthenticationModel.from(authentication);

        assertThat(model.getKind()).isEqualTo(AuthenticationKind.DELEGATED);
        assertThat(model.getPrincipal().getKind()).isEqualTo(ActorKind.USER);
        assertThat(model.getPrincipal().getClaims()).containsEntry(JwtClaimNames.SUB, "user-1");
        assertThat(model.getActor().getKind()).isEqualTo(ActorKind.EXTENSION);
        assertThat(model.getActor().getSub()).isEqualTo("extension-1");
    }

    @Test
    void noAuthenticationDetails_yieldsAnonymous() {
        doReturn(List.of()).when(authentication).getAuthorities();

        var model = AuthenticationModel.from(authentication);

        assertThat(model.getKind()).isEqualTo(AuthenticationKind.ANONYMOUS);
        assertThat(model.isAuthenticated()).isFalse();
    }
}
