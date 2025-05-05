package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.registry.ApplicationRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class ApplicationArgumentResolverConfiguration implements WebMvcConfigurer {
    private final ApplicationRegistry applicationRegistry;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ApplicationArgumentResolver(applicationRegistry));
    }
}
