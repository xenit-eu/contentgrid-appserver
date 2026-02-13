package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
public class ContentGridHandlerMappingConfiguration {

    @Bean
    RequestMappingHandlerMapping requestMappingHandlerMapping(
            ApplicationResolver applicationResolver,
            ApplicationNameExtractor applicationNameExtractor
    ) {
        return new DynamicDispatchApplicationHandlerMapping(
                applicationResolver,
                applicationNameExtractor
        );
    }

}
