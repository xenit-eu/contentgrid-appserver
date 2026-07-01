package com.contentgrid.appserver.autoconfigure.security.authority;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
        return Optional.ofNullable(actorConverter.convert(source))
                .map(PrincipalAuthenticationDetailsGrantedAuthority::new)
                .map(List::<GrantedAuthority>of)
                .orElseGet(List::of);
    }
}
