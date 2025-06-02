package com.contentgrid.appserver.rest.links.curie;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.mediatype.MediaTypeConfigurationCustomizer;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration;
import org.springframework.hateoas.mediatype.hal.HalConfiguration.RenderSingleLinks;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.RepresentationModelProcessor;

/**
 * Copied from contentgrid-spring.
 *
 * @see <a href="https://github.com/xenit-eu/contentgrid-spring/blob/6a6d35ad5ca692a96a31aab764c4b74929db07ca/contentgrid-spring-data-rest/src/main/java/com/contentgrid/spring/data/rest/hal/ContentGridCurieConfiguration.java">ContentGridCurieConfiguration</a>
 */
@Configuration(proxyBeanMethods = false)
public class ContentGridCurieConfiguration {

    @Bean
    MediaTypeConfigurationCustomizer<HalConfiguration> contentGridCuriesMediaTypeConfigurationCustomizer() {
        return halConfiguration -> halConfiguration
                .withRenderSingleLinksFor(HalLinkRelation.CURIES, RenderSingleLinks.AS_ARRAY);
    }

    @Bean
    CurieProvider contentGridCurieProvider(ObjectProvider<CurieProviderCustomizer> customizers) {
        CurieProviderBuilder curieProvider = new ContentGridCurieProvider();

        for (CurieProviderCustomizer customizer : customizers) {
            curieProvider = customizer.customize(curieProvider);
        }

        return curieProvider.build();
    }

    @Bean
    RepresentationModelProcessor<CollectionModel<?>> contentGridSpringDataEmbeddedCuriesResourceProcessor(
            LinkRelationProvider linkRelationProvider, CurieProvider curieProvider
    ) {
        return new SpringDataEmbeddedCuriesResourceProcessor(linkRelationProvider, curieProvider);
    }
}
