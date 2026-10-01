package com.contentgrid.appserver.rest.mapping;

import com.contentgrid.appserver.registry.ApplicationNameExtractor;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.rest.mapping.specialized.ContentGridSpecializationHandlerConfiguration;
import com.contentgrid.appserver.rest.mapping.specialized.SpecializationHandler;
import java.lang.reflect.Method;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration(proxyBeanMethods = false)
@Import(ContentGridSpecializationHandlerConfiguration.class)
public class ContentGridHandlerMappingConfiguration {

    @Bean
    WebMvcRegistrations webMvcRegistrations(
            ApplicationResolver applicationResolver,
            ApplicationNameExtractor applicationNameExtractor,
            SpecializationHandler<Method> specializationHandler
    ) {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new DynamicDispatchApplicationHandlerMapping(
                        applicationResolver,
                        applicationNameExtractor,
                        specializationHandler
                );
            }
        };
    }

}
