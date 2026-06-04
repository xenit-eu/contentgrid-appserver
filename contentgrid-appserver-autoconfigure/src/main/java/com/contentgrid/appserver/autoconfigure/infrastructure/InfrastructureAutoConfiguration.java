package com.contentgrid.appserver.autoconfigure.infrastructure;

import com.contentgrid.appserver.autoconfigure.infrastructure.InfrastructureAutoConfiguration.BlueprintArtifactProperties;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifact;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactReference;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactReferenceResolver;
import com.contentgrid.appserver.infrastructure.api.BlueprintArtifactReferenceResolverRegistry;
import com.contentgrid.appserver.infrastructure.impl.fs.FilesystemBlueprintArtifactReferenceResolver;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnClass({BlueprintArtifact.class, BlueprintArtifactReferenceResolverRegistry.class})
@EnableConfigurationProperties(BlueprintArtifactProperties.class)
public class InfrastructureAutoConfiguration {

    private final ApplicationContext applicationContext;

    @ConfigurationProperties(prefix = "contentgrid.appserver.blueprint-artifact")
    public record BlueprintArtifactProperties(
            @DefaultValue("classpath:.") @NonNull String location
    ) {}

    @Bean
    @ConditionalOnClass(FilesystemBlueprintArtifactReferenceResolver.class)
    BlueprintArtifactReferenceResolver filesystemBlueprintArtifactReferenceResolver() {
        return new FilesystemBlueprintArtifactReferenceResolver(applicationContext.getClassLoader());
    }

    @Bean
    @Primary
    BlueprintArtifactReferenceResolver blueprintArtifactReferenceResolverRegistry(List<BlueprintArtifactReferenceResolver> blueprintArtifactReferenceResolvers) {
        return new BlueprintArtifactReferenceResolverRegistry(blueprintArtifactReferenceResolvers);
    }

    @Bean
    @ConditionalOnMissingBean
    BlueprintArtifact defaultBlueprintArtifact(BlueprintArtifactReferenceResolver resolver, BlueprintArtifactProperties properties) {
        return resolver.resolve(BlueprintArtifactReference.of(properties.location()));
    }
}
