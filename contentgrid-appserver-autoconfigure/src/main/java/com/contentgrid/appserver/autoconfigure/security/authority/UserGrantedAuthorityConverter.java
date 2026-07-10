package com.contentgrid.appserver.autoconfigure.security.authority;

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.Jwt;

@RequiredArgsConstructor
public class UserGrantedAuthorityConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final Converter<ClaimAccessor, Actor> actorConverter;

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        var actor = actorConverter.convert(source);
        if (actor == null) {
            return List.of();
        }
        if (actor.parent() != null) {
            var principal = new Actor(actor.type(), actor.claims());
            return List.of(new DelegatedAuthenticationDetailsGrantedAuthority(principal, actor.parent()));
        }
        return List.of(new PrincipalAuthenticationDetailsGrantedAuthority(actor));
    }
}
