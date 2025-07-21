package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
public class ContentGridHandlerMappingConfiguration {

    @Bean
    WebMvcRegistrations webMvcRegistrations(
            ApplicationResolver applicationResolver,
            ApplicationNameExtractor applicationNameExtractor
    ) {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new DynamicDispatchApplicationHandlerMapping(
                        applicationResolver,
                        applicationNameExtractor
                );
            }
        };
    }

}
