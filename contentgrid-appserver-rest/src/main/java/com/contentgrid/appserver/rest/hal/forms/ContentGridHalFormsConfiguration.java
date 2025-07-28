package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.registry.ApplicationResolver;
import com.contentgrid.appserver.registry.SingleApplicationResolver;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.hal.forms.HalFormsConfiguration;

@Configuration
public class ContentGridHalFormsConfiguration {

    @Bean
    MediaTypeConfigurationCustomizer<HalFormsConfiguration> halFormsOptionsMetadataCustomizer(ApplicationResolver applicationResolver) {
        if (applicationResolver instanceof SingleApplicationResolver singleApplicationResolver) {
            return new HalFormsOptionsMetadataCustomizer(() -> List.of(singleApplicationResolver.getApplication()));
        }
        return new HalFormsOptionsMetadataCustomizer(List::of); // still handles options for sort property
    }

    @Bean
    HalFormsPayloadMetadataContributor defaultHalFormsPayloadMetadataContributor() {
        return new HalFormsPayloadMetadataContributor();
    }

    @Bean
    HalFormsPayloadMetadataConverter defaultHalFormsPayloadMetadataConverter(List<HalFormsPayloadMetadataContributor> contributors) {
        return new HalFormsPayloadMetadataConverter(contributors);
    }

}
