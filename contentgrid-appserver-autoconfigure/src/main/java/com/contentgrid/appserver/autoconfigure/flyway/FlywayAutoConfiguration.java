package com.contentgrid.appserver.autoconfigure.flyway;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Flyway.class)
public class FlywayAutoConfiguration {

    @Bean
    @ConditionalOnBean(BlueprintArtifact.class)
    FlywayConfigurationCustomizer blueprintArtifactResourceProviderFlywayConfigurationCustomizer(
            BlueprintArtifact blueprintArtifact) {
        var resourceProvider = new BlueprintArtifactFlywayResourceProvider(blueprintArtifact);
        return configuration -> configuration.resourceProvider(resourceProvider);
    }
}
