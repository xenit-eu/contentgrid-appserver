package com.contentgrid.appserver.autoconfigure.flyway;

import com.contentgrid.appserver.domain.spi.blueprintartifact.BlueprintArtifact;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Flyway.class)
public class FlywayPostgresAutoConfiguration {

    @Bean
    FlywayConfigurationCustomizer blueprintArtifactResourceProviderFlywayConfigurationCustomizer(
            BlueprintArtifact blueprintArtifact) {
        var resourceProvider = new BlueprintArtifactFlywayResourceProvider(blueprintArtifact);
        return configuration -> configuration.resourceProvider(resourceProvider);
    }
}
