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
    private static final List<String> DEFAULT_STATIC_RESOURCE_LOCATIONS = List.of(
            "archive:META-INF/resources", "archive:resources", "archive:static", "archive:public"
    );

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
    @ConfigurationProperties("contentgrid.web.resources")
    ContentGridStaticResourcesProperties contentGridStaticResourcesProperties() {
        return new ContentGridStaticResourcesProperties();
    }

    @Bean
    FlywayConfigurationCustomizer springFlywayLocationsCustomizer(ArchiveExtractionTempDirProvider tempDirProvider, ContentGridFlywayProperties flywayProperties) {
        var locations = configureLocations(flywayProperties.getLocations(), DEFAULT_FLYWAY_LOCATIONS, tempDirProvider);
        return configuration -> configuration.locations(locations);
    }

//    @Bean
//    @Primary
//    ResourceHandlerRegistrationCustomizer staticResourceHandlerRegistrationCustomizer(ArchiveExtractionTempDirProvider tempDirProvider, ContentGridStaticResourcesProperties staticResourcesProperties) {
//        var locations = configureLocations(staticResourcesProperties.getLocations(), DEFAULT_STATIC_RESOURCE_LOCATIONS, tempDirProvider);
//        return registration -> registration.addResourceLocations(locations);
//    }

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

    @Data
    public static class ContentGridStaticResourcesProperties {
        private List<String> locations;
    }
}
