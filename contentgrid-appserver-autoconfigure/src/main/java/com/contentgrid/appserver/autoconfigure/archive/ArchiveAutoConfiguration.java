package com.contentgrid.appserver.autoconfigure.archive;

import java.util.List;
import lombok.Data;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty("contentgrid.appserver.archive")
public class ArchiveAutoConfiguration {

    private static final List<String> DEFAULT_FLYWAY_LOCATIONS = List.of("archive:db/migration");

    @Bean
    public ArchiveExtractionBeanFactoryPostProcessor archiveExtractionPostProcessor() {
        return new ArchiveExtractionBeanFactoryPostProcessor();
    }

    @Bean
    @ConfigurationProperties("contentgrid.flyway")
    ContentGridFlywayProperties contentGridFlywayProperties() {
        return new ContentGridFlywayProperties();
    }

    @Bean
    FlywayConfigurationCustomizer springFlywayLocationsCustomizer(ArchiveExtractionTempDirProvider tempDirProvider, ContentGridFlywayProperties flywayProperties) {
        var locations = configureLocations(flywayProperties.getLocations(), DEFAULT_FLYWAY_LOCATIONS, tempDirProvider);
        return configuration -> configuration.locations(locations);
    }

    private String[] configureLocations(List<String> locations, List<String> defaultLocations, ArchiveExtractionTempDirProvider tempDirProvider) {
        if (locations == null || locations.isEmpty()) {
            locations = defaultLocations;
        }
        return locations.stream()
                .map(location -> {
                    if (location.startsWith(ArchiveProtocolResolver.ARCHIVE_PREFIX)) {
                        var relativePath = location.substring(ArchiveProtocolResolver.ARCHIVE_PREFIX.length());
                        return "filesystem:" + tempDirProvider.getTempDir().resolve(relativePath).normalize();
                    } else {
                        return location;
                    }
                }).toArray(String[]::new);
    }

    @Data
    public static class ContentGridFlywayProperties {
        private List<String> locations;
    }
}
