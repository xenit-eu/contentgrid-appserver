package com.contentgrid.appserver.rest.links.factory;

import java.net.URI;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;

/**
 * Factory for creating {@link Link}s with a certain {@link LinkRelation}
 * @see CustomizableLinkFactory for customizing the parameters of the generated {@link Link}
 */
public interface LinkFactory {

    /**
     * Create a link with the attributes configured on this factory
     *
     * @param linkRelation The link relation to use for the link
     * @return A new link
     */
    Link withRel(LinkRelation linkRelation);

    /**
     * @return The URI of the link that is built by this factory
     */
    default URI toUri() {
        return withSelfRel().toUri();
    }

    /**
     * Create a link with the <code>self</code> link relation. A <code>self</code>-link provides the link for the current page.
     * <p>
     * A <code>self</code>-link does not have most link attributes that would be present for other instances of the link.
     * In particular, it does not duplicate information about the current page that is also provided in other ways, like via other links or HTTP headers.
     * @return A new link
     */
    default Link withSelfRel() {
        return withRel(IanaLinkRelations.SELF)
                // Self-link should not have all these properties:
                // The self-link describes the current page, so:
                .withName(null) // name is not used for disambiguation
                // But title is present; it is basically the human-readable title of this page
                .withProfile(null) // profile is provided with a separate 'profile' link
                .withType(null) // Content-Type is provided using HTTP headers
                .withHreflang(null) // Language is negotiated using HTTP Headers
                .withDeprecation(null); // Deprecation information about the page is provided using HTTP headers
    }
}
