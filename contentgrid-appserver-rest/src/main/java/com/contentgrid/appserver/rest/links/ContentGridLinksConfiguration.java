package com.contentgrid.appserver.rest.links;

import com.contentgrid.appserver.rest.links.curie.ContentGridCurieConfiguration;
import com.contentgrid.appserver.rest.links.curie.CurieProviderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.mediatype.hal.HalConfiguration.RenderSingleLinks;

@Configuration
@Import(ContentGridCurieConfiguration.class)
public class ContentGridLinksConfiguration {

    @Bean
    CurieProviderCustomizer contentGridCurieProviderCustomizer() {
        return CurieProviderCustomizer.register(ContentGridLinkRelations.CURIE, ContentGridLinkRelations.TEMPLATE);
    }

    @Bean
    MediaTypeConfigurationCustomizer<HalConfiguration> contentGridLinksMediaTypeConfigurationCustomizer() {
        return halConfiguration -> halConfiguration
                .withRenderSingleLinksFor(ContentGridLinkRelations.CONTENT, RenderSingleLinks.AS_ARRAY)
                .withRenderSingleLinksFor(ContentGridLinkRelations.RELATION, RenderSingleLinks.AS_ARRAY)
                .withRenderSingleLinksFor(ContentGridLinkRelations.ENTITY, RenderSingleLinks.AS_ARRAY);
    }

}
