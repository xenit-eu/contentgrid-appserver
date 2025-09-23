package com.contentgrid.appserver.rest.links.factory;

/**
 * A {@link LinkFactory} that allows customizing attributes of the {@link org.springframework.hateoas.Link} that will
 * be generated with a relation.
 */
public interface CustomizableLinkFactory extends LinkFactory {
    CustomizableLinkFactory withName(String name);
    CustomizableLinkFactory withTitle(String title);
    CustomizableLinkFactory withType(String type);
    CustomizableLinkFactory withProfile(String profile);
    CustomizableLinkFactory withDeprecation(String deprecation);
    CustomizableLinkFactory withHreflang(String name);

}
