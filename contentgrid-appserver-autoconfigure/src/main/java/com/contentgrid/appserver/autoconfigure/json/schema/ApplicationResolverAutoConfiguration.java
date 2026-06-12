package com.contentgrid.appserver.autoconfigure.json.schema;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.json.DefaultApplicationSchemaConverter;
import com.contentgrid.appserver.application.model.values.ApplicationName;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.BlueprintArtifactApplicationResolver;
import com.contentgrid.appserver.registry.CachingApplicationResolver;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({Application.class, ApplicationResolver.class})
public class ApplicationResolverAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass({BlueprintArtifactApplicationResolver.class, DefaultApplicationSchemaConverter.class})
    @ConditionalOnBean(BlueprintArtifact.class)
    ApplicationResolver blueprintArtifactApplicationResolver(BlueprintArtifact blueprintArtifact) {
        return new CachingApplicationResolver(new BlueprintArtifactApplicationResolver(blueprintArtifact));
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
