package com.contentgrid.appserver.autoconfigure.flyway;

import com.contentgrid.appserver.infrastructure.api.Artifact;
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
    FlywayConfigurationCustomizer infrastructureResourceProviderFlywayConfigurationCustomizer(Artifact artifact) {
        var resourceProvider = new ArtifactFlywayResourceProvider(artifact.subDir(Path.of("db", "migration")));
        return configuration -> configuration.resourceProvider(resourceProvider);
    }
}
