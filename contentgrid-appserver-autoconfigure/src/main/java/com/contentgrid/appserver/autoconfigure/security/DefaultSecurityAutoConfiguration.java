package com.contentgrid.appserver.autoconfigure.security;

import com.contentgrid.appserver.autoconfigure.actuator.ContentgridActuatorAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration(before = ContentgridActuatorAutoConfiguration.class)
@ConditionalOnClass(SecurityFilterChain.class)
public class DefaultSecurityAutoConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoder) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());

        if (jwtDecoder.getIfAvailable() != null) {
            http.oauth2ResourceServer((oauth2) -> oauth2.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }

}
