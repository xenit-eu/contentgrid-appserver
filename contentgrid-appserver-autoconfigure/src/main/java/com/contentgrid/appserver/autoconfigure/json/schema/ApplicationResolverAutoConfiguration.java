package com.contentgrid.appserver.autoconfigure.json.schema;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.json.ApplicationSchemaConverter;
import com.contentgrid.appserver.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.json.exceptions.InvalidJsonException;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.DefaultApplicationNameExtractor;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

@AutoConfiguration
@ConditionalOnClass({Application.class, SingleApplicationResolver.class, DefaultApplicationNameExtractor.class})
@ConditionalOnProperty("contentgrid.appserver.json.source")
public class ApplicationResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ApplicationResolver applicationResolver(ApplicationSchemaConverter applicationSchemaConverter, @Value("${contentgrid.appserver.json.source}") String path) throws IOException, InvalidJsonException {
        var resource = new ClassPathResource(path);
        var application = applicationSchemaConverter.convert(resource.getInputStream());
        return new SingleApplicationResolver(application);
    }

    @Bean
    ApplicationSchemaConverter applicationSchemaConverter() {
        return new DefaultApplicationSchemaConverter();
    }
}
