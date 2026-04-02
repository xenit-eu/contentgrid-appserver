package com.contentgrid.appserver.autoconfigure.archive;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty("contentgrid.appserver.archive")
public class ArchiveAutoConfiguration {

    @Bean
    public ArchiveExtractionBeanFactoryPostProcessor archiveExtractionPostProcessor() {
        return new ArchiveExtractionBeanFactoryPostProcessor();
    }
}
