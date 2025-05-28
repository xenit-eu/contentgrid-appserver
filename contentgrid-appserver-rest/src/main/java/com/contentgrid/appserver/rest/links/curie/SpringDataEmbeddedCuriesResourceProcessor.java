package com.contentgrid.appserver.rest.links.curie;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import org.springframework.hateoas.server.LinkRelationProvider;
import org.springframework.hateoas.server.RepresentationModelProcessor;
import org.springframework.hateoas.server.core.EmbeddedWrapper;

/**
 * Adds curies to embedded resources when spring-hateoas doesn't want to add them
 * <p>
 * This processor must run with lowest precedence, so it executes after any other resource processors.
 *
 * @see <a href="https://github.com/xenit-eu/contentgrid-spring/blob/6a6d35ad5ca692a96a31aab764c4b74929db07ca/contentgrid-spring-data-rest/src/main/java/com/contentgrid/spring/data/rest/links/SpringDataEmbeddedCuriesResourceProcessor.java">SpringDataEmbeddedCuriesResourceProcessor</a>
 */
@RequiredArgsConstructor
public class SpringDataEmbeddedCuriesResourceProcessor implements RepresentationModelProcessor<CollectionModel<?>>,
        Ordered {

    private final LinkRelationProvider linkRelationProvider;
    private final CurieProvider curieProvider;

    @Override
    public CollectionModel<?> process(CollectionModel<?> model) {
        var content = model.getContent();

        var linkCuries = model.getLinks()
                .stream()
                .anyMatch(this::isCuriedLink);

        var embeddedCuries = content.stream()
                .filter(EmbeddedWrapper.class::isInstance)
                .map(EmbeddedWrapper.class::cast)
                .anyMatch(this::hasCuriedRelation);

        if(linkCuries || embeddedCuries) {
            return model;
        }

        var curies = curieProvider.getCurieInformation(model.getLinks());

        for (Object curie : curies) {
            model.add((Link) curie);
        }

        return model;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean hasCuriedRelation(EmbeddedWrapper embeddedWrapper) {
        if(embeddedWrapper.getRel().isPresent()) {
            return isCuriedRel(embeddedWrapper.getRel().get());
        }
        var objectType = embeddedWrapper.getRelTargetType();

        var resourceRel = embeddedWrapper.isCollectionValue()?
                linkRelationProvider.getCollectionResourceRelFor(objectType):
                linkRelationProvider.getItemResourceRelFor(objectType);

        return isCuriedRel(resourceRel);
    }

    private boolean isCuriedLink(Link link) {
        return isCuriedRel(link.getRel());
    }

    private boolean isCuriedRel(LinkRelation relation) {
        return HalLinkRelation.of(relation).isCuried();
    }

}
