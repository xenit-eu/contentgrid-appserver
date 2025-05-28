package com.contentgrid.appserver.rest.links;

import lombok.experimental.UtilityClass;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;

/**
 * Copied from contentgrid-spring.
 *
 * @see <a href="https://github.com/xenit-eu/contentgrid-spring/blob/6a6d35ad5ca692a96a31aab764c4b74929db07ca/contentgrid-spring-data-rest/src/main/java/com/contentgrid/spring/data/rest/links/ContentGridLinkRelations.java">ContentGridLinkRelations</a>
 */
@UtilityClass
public class ContentGridLinkRelations {
    static final String CURIE = "cg";
    static final UriTemplate TEMPLATE = UriTemplate.of("https://contentgrid.cloud/rels/contentgrid/{rel}");

    public static final LinkRelation ENTITY = HalLinkRelation.curied(CURIE, "entity");
    public static final LinkRelation RELATION = HalLinkRelation.curied(CURIE, "relation");
    public static final LinkRelation CONTENT = HalLinkRelation.curied(CURIE, "content");

}
