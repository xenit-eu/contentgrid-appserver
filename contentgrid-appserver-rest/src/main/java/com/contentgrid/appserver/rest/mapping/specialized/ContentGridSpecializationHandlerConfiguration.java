package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.rest.mapping.SpecializedOnEntity;
import com.contentgrid.appserver.rest.mapping.SpecializedOnPropertyType;
import java.lang.reflect.Method;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class ContentGridSpecializationHandlerConfiguration {

    @Bean
    @Primary
    SpecializationHandler<Method> compositeSpecializationHandler(List<SpecializationHandler<Method>> specialisationHandlers) {
        return new CompositeSpecializationHandler<>(specialisationHandlers);
    }

    @Bean
    SpecializationHandler<Method> specializedOnEntityHandler() {
        return new AnnotationBasedSpecializationHandlerAdapter<>(
                SpecializedOnEntity.class,
                new SpecializedOnEntityAnnotationSpecialisationHandler()
        );
    }

    @Bean
    SpecializationHandler<Method> specializedOnPropertyTypeHandler() {
        return new AnnotationBasedSpecializationHandlerAdapter<>(
                SpecializedOnPropertyType.class,
                new SpecializedOnPropertyTypeAnnotationSpecialisationHandler()
        );
    }


}
