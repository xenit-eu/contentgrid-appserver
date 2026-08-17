package com.contentgrid.appserver.autoconfigure.blueprintartifact;

import com.contentgrid.appserver.autoconfigure.blueprintartifact.BlueprintArtifactAutoConfiguration.BlueprintArtifactProperties;
import com.contentgrid.appserver.blueprintartifact.impl.fs.FilesystemBlueprintArtifactReferenceResolver;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReference;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReferenceResolver;
import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifactReferenceResolverRegistry;
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
public class BlueprintArtifactAutoConfiguration {

    private final ApplicationContext applicationContext;

    @ConfigurationProperties(prefix = "contentgrid.appserver.blueprint-artifact")
    public record BlueprintArtifactProperties(
            @DefaultValue("classpath:.") @NonNull String location,
            S3Properties s3
    ) {
        public record S3Properties(
                @NonNull String endpoint,
                String accessKey,
                String secretKey,
                String region,
                @DefaultValue("true") boolean pathStyleAccess
        ) {}
    }

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
