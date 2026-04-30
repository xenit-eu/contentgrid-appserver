package com.contentgrid.appserver.autoconfigure.json.schema;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.json.exceptions.InvalidJsonException;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.ArtifactApplicationResolver;
import com.contentgrid.appserver.registry.DefaultApplicationNameExtractor;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

@AutoConfiguration
@ConditionalOnClass({Application.class, SingleApplicationResolver.class, DefaultApplicationNameExtractor.class})
public class ApplicationResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("contentgrid.appserver.application-model")
    ApplicationResolver applicationResolver(@Value("${contentgrid.appserver.application-model}") Resource resource) throws IOException, InvalidJsonException {
        var applicationSchemaConverter = new DefaultApplicationSchemaConverter();
        var application = applicationSchemaConverter.convert(resource.getInputStream());
        return new SingleApplicationResolver(application);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "contentgrid.appserver.application-model", havingValue = "false", matchIfMissing = true)
    ApplicationResolver artifactApplicationResolver(Artifact artifact) {
        return new ArtifactApplicationResolver(artifact);
    }
}
