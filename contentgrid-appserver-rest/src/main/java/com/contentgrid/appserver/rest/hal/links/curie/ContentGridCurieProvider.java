package com.contentgrid.appserver.rest.hal.links.curie;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.IanaUriSchemes;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.UriTemplate;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;

/**
 * Copied from contentgrid-spring.
 *
 * @see <a href="https://github.com/xenit-eu/contentgrid-spring/blob/6a6d35ad5ca692a96a31aab764c4b74929db07ca/contentgrid-spring-data-rest/src/main/java/com/contentgrid/spring/data/rest/hal/ContentGridCurieProvider.java">ContentGridCurieProvider</a>
 */
@RequiredArgsConstructor
class ContentGridCurieProvider implements CurieProvider, CurieProviderBuilder {

    private final Map<String, UriTemplate> curies;

    public ContentGridCurieProvider() {
        this(Map.of());
    }

    @Override
    public HalLinkRelation getNamespacedRelFrom(Link link) {
        return getNamespacedRelFor(link.getRel());
    }

    @Override
    public HalLinkRelation getNamespacedRelFor(LinkRelation rel) {
        assertRegisteredCurie(rel);
        return HalLinkRelation.of(rel);
    }

    @Override
    public Collection<?> getCurieInformation(Links links) {
        return curies.entrySet().stream()
                .map(it -> createCurieLink(it.getKey(), it.getValue()))
                .toList();
    }

    private Link createCurieLink(String name, UriTemplate template) {
        return Link.of(
                template,
                HalLinkRelation.CURIES
        ).withName(name);
    }

    @Override
    public CurieProviderBuilder withCurie(String prefix, UriTemplate template) {
        if(curies.containsKey(prefix)) {
            throw new IllegalArgumentException("CURIE prefix '%s' is already registered with template '%s' and can not be re-registered with template '%s'.".formatted(
                    prefix,
                    curies.get(prefix),
                    template
            ));
        }
        if(IanaUriSchemes.isIanaUriScheme(prefix)) {
            throw new IllegalArgumentException("CURIE prefix '%s' can not be an IANA-registered URI scheme.".formatted(prefix));
        }
        var newCuries = new HashMap<>(this.curies);
        newCuries.put(prefix, template);
        return new ContentGridCurieProvider(newCuries);
    }

    @Override
    public CurieProvider build() {
        return this;
    }

    private void assertRegisteredCurie(LinkRelation rel) {
        var relation = rel.value();
        int firstColonIndex = relation.indexOf(':');

        String curie = firstColonIndex == -1 ? null : relation.substring(0, firstColonIndex);

        if(curie == null) {
            // Not curie -> need to check if it's a registered link relation
            if(!isIanaRel(relation) && !HalLinkRelation.CURIES.isSameAs(rel)) {
                throw new IllegalArgumentException("Relation '%s' is not an IANA-registered relation".formatted(relation));
            }
            return;
        }

        if(IanaUriSchemes.isIanaUriScheme(curie)) {
            // Not a curie, but a RFC 5988 #4.2a extension relation type
            return;
        }

        if(!curies.containsKey(curie)) {
            throw new IllegalArgumentException("Relation '%s' uses CURIE that is not registered".formatted(relation));
        }
    }


    /**
     * Constructed using
     * <code>
     * curl -s https://www.iana.org/assignments/link-relations/link-relations.xml \
     *     | xmllint --xpath '//*[local-name()="value"]/text()' - \
     *     | awk '{printf "\"%s\", ", $0}' \
     *     | sed 's/, $/\n/'
     * </code>
     */
    private static final Set<String> IANA_RELS = Set.of(
            "about", "acl", "alternate", "amphtml", "api-catalog", "appendix", "apple-touch-icon", "apple-touch-startup-image", "archives", "author", "blocked-by", "bookmark", "c2pa-manifest", "canonical", "chapter", "cite-as", "collection", "compression-dictionary", "contents", "convertedfrom", "copyright", "create-form", "current", "deprecation", "describedby", "describes", "disclosure", "dns-prefetch", "duplicate", "edit", "edit-form", "edit-media", "enclosure", "external", "first", "geofeed", "glossary", "help", "hosts", "hub", "ice-server", "icon", "index", "intervalafter", "intervalbefore", "intervalcontains", "intervaldisjoint", "intervalduring", "intervalequals", "intervalfinishedby", "intervalfinishes", "intervalin", "intervalmeets", "intervalmetby", "intervaloverlappedby", "intervaloverlaps", "intervalstartedby", "intervalstarts", "item", "last", "latest-version", "license", "linkset", "lrdd", "manifest", "mask-icon", "me", "media-feed", "memento", "micropub", "modulepreload", "monitor", "monitor-group", "next", "next-archive", "nofollow", "noopener", "noreferrer", "opener", "openid2.local_id", "openid2.provider", "original", "p3pv1", "payment", "pingback", "preconnect", "predecessor-version", "prefetch", "preload", "prerender", "prev", "preview", "previous", "prev-archive", "privacy-policy", "profile", "publication", "rdap-active", "rdap-bottom", "rdap-down", "rdap-top", "rdap-up", "related", "restconf", "replies", "ruleinput", "search", "section", "self", "service", "service-desc", "service-doc", "service-meta", "sip-trunking-capability", "sponsored", "start", "status", "stylesheet", "subsection", "successor-version", "sunset", "tag", "terms-of-service", "timegate", "timemap", "type", "ugc", "up", "version-history", "via", "webmention", "working-copy", "working-copy-of"
    );

    private static boolean isIanaRel(String relation) {
        return IANA_RELS.contains(relation) || IANA_RELS.stream().anyMatch(relation::equalsIgnoreCase);
    }
}
