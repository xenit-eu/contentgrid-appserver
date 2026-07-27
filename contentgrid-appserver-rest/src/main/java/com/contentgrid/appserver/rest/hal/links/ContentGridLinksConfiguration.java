package com.contentgrid.appserver.rest.hal.links;

import com.contentgrid.appserver.rest.hal.links.curie.ContentGridCurieConfiguration;
import com.contentgrid.appserver.rest.hal.links.curie.CurieProviderCustomizer;
import com.contentgrid.appserver.rest.hal.serializer.HalModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;

@Configuration
@Import(ContentGridCurieConfiguration.class)
public class ContentGridLinksConfiguration {

    @Bean
    CurieProviderCustomizer contentGridCurieProviderCustomizer() {
        return CurieProviderCustomizer.register(ContentGridLinkRelations.CURIE, ContentGridLinkRelations.TEMPLATE);
    }

    @Bean
    MediaTypeConfigurationCustomizer<HalConfiguration> contentGridHalModuleMediaTypeConfigurationCustomizer() {
        return halConfiguration -> halConfiguration
                .withMapperBuilderCustomizer(builder -> builder.addModule(new HalModule()));
    }

}
