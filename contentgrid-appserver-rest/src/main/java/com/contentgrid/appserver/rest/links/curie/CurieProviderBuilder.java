package com.contentgrid.appserver.rest.links.curie;

import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.CurieProvider;

/**
 * Fluent builder for {@link CurieProvider}
 *
 * @see <a href="https://github.com/xenit-eu/contentgrid-spring/blob/6a6d35ad5ca692a96a31aab764c4b74929db07ca/contentgrid-spring-data-rest/src/main/java/com/contentgrid/spring/data/rest/hal/CurieProviderBuilder.java">CurieProviderBuilder</a>
 */
public interface CurieProviderBuilder {

    /**
     * Adds a mapping from CURIE prefix to a {@link UriTemplate} for resolving the CURIE against
     * @param prefix CURIE prefix
     * @param template Template to use to resolve the CURIE
     * @return Copy with new curie mapping applied
     */
    CurieProviderBuilder withCurie(String prefix, UriTemplate template);

    /**
     * Builds a {@link CurieProvider} with the mappings specified in the builder
     * @return An immutable {@link CurieProvider} instance
     */
    CurieProvider build();
}
