package com.contentgrid.appserver.rest.links;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.mediatype.hal.HalConfiguration.RenderSingleLinks;

@Configuration
public class ContentGridLinksConfiguration {

    @Bean
    MediaTypeConfigurationCustomizer<HalConfiguration> contentGridLinksMediaTypeConfigurationCustomizer() {
        return halConfiguration -> halConfiguration
                .withRenderSingleLinksFor(ContentGridLinkRelations.CONTENT, RenderSingleLinks.AS_ARRAY)
                .withRenderSingleLinksFor(ContentGridLinkRelations.RELATION, RenderSingleLinks.AS_ARRAY)
                .withRenderSingleLinksFor(ContentGridLinkRelations.ENTITY, RenderSingleLinks.AS_ARRAY);
    }

}
