package com.contentgrid.appserver.rest.problem;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.core.annotation.Order;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;

@RequiredArgsConstructor
@EnableHypermediaSupport(type = HypermediaType.HTTP_PROBLEM_DETAILS)
@Configuration(proxyBeanMethods = false)
public class ContentgridProblemDetailConfiguration {

    @Bean
    ProblemTypeUrlFactory contentGridProblemTypeUrlFactory() {
        return new ProblemTypeUrlFactory(UriTemplate.of("https://contentgrid.cloud/problems{/item*}"));
    }

    @Bean
    ProblemFactory contentGridProblemFactory(ProblemTypeUrlFactory problemTypeUrlFactory) {
        return new ProblemFactory(ProblemTypeMessageSource.getAccessor(), problemTypeUrlFactory);
    }

    @Bean
    @Order(-1)
    ProblemDetailsExceptionHandler contentGridProblemDetailsExceptionHandler(ProblemFactory problemFactory) {
        return new ProblemDetailsExceptionHandler(problemFactory);
    }

}
