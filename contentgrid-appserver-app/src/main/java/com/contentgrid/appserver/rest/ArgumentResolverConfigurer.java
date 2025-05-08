package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.registry.ApplicationResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@RequiredArgsConstructor
public class ArgumentResolverConfigurer implements WebMvcConfigurer {
    private final ApplicationResolver applicationResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ApplicationArgumentResolver(applicationResolver));
    }
}
