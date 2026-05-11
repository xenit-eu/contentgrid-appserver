package com.contentgrid.appserver.autoconfigure.json.schema;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.json.exceptions.InvalidJsonException;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.ArtifactApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import java.io.IOException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

@AutoConfiguration
@ConditionalOnClass({Application.class, ApplicationResolver.class})
public class ApplicationResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass({SingleApplicationResolver.class, DefaultApplicationSchemaConverter.class})
    @ConditionalOnProperty("contentgrid.appserver.application-model")
    ApplicationResolver applicationResolver(@Value("${contentgrid.appserver.application-model}") Resource resource) throws IOException, InvalidJsonException {
        var applicationSchemaConverter = new DefaultApplicationSchemaConverter();
        var application = applicationSchemaConverter.convert(resource.getInputStream());
        return new SingleApplicationResolver(application);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass({ArtifactApplicationResolver.class, DefaultApplicationSchemaConverter.class})
    @ConditionalOnProperty(name = "contentgrid.appserver.application-model", havingValue = "false", matchIfMissing = true)
    ApplicationResolver artifactApplicationResolver(Artifact artifact) {
        return new ArtifactApplicationResolver(artifact);
    }

    @Bean
    ApplicationResolverValidator applicationResolverValidator(ObjectProvider<ApplicationResolver> applicationResolver) {
        return new ApplicationResolverValidator(applicationResolver);
    }

    @RequiredArgsConstructor
    private static class ApplicationResolverValidator implements InitializingBean {

        private static final ApplicationName APPLICATION_NAME = ApplicationName.of("default");

        private final ObjectProvider<ApplicationResolver> applicationResolver;

        @Override
        public void afterPropertiesSet() {
            // Make sure resolving an application works.
            // If not, crash on application startup instead of on every request
            Objects.requireNonNull(applicationResolver.getObject().resolve(APPLICATION_NAME));
        }
    }
}
