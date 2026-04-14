package com.contentgrid.appserver.autoconfigure.infrastructure;

import com.contentgrid.appserver.autoconfigure.infrastructure.InfrastructureAutoConfiguration.InfrastructureProperties;
import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactReference;
import com.contentgrid.appserver.infrastructure.api.ArtifactReferenceResolver;
import com.contentgrid.appserver.infrastructure.api.ArtifactReferenceResolverRegistry;
import com.contentgrid.appserver.infrastructure.impl.fs.FilesystemArtifactReferenceResolver;
import java.util.List;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
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
@ConditionalOnClass({Artifact.class, ArtifactReferenceResolverRegistry.class})
@EnableConfigurationProperties(InfrastructureProperties.class)
public class InfrastructureAutoConfiguration {

    @Autowired
    private ApplicationContext applicationContext;

    @ConfigurationProperties(prefix = "contentgrid.appserver.infrastructure")
    public record InfrastructureProperties(
            @DefaultValue("classpath:.") @NonNull String location
    ) {}

    @Bean
    @ConditionalOnClass(FilesystemArtifactReferenceResolver.class)
    ArtifactReferenceResolver filesystemArtifactReferenceResolver() {
        return new FilesystemArtifactReferenceResolver(applicationContext.getClassLoader());
    }

    @Bean
    @Primary
    ArtifactReferenceResolver artifactReferenceResolverRegistry(List<ArtifactReferenceResolver> artifactReferenceResolvers) {
        return new ArtifactReferenceResolverRegistry(artifactReferenceResolvers);
    }

    @Bean
    @ConditionalOnMissingBean
    Artifact defaultArtifact(ArtifactReferenceResolver resolver, InfrastructureProperties properties) {
        var reference = ArtifactReference.parse(properties.location());
        return resolver.resolve(reference);
    }
}
