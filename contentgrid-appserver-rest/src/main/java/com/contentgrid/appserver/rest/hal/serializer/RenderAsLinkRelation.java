package com.contentgrid.appserver.rest.hal.serializer;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.mediatype.hal.HalConfiguration.RenderSingleLinks;

/**
 * Marks a link relation as always rendering as a single link or always as a list of links
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
public class RenderAsLinkRelation implements LinkRelation {
    @Delegate
    @NonNull
    private final LinkRelation delegate;

    @NonNull
    @Getter
    private final RenderSingleLinks renderSingleLinks;

    /**
     * Marks a relation as always being rendered as a single link
     */
    public static RenderAsLinkRelation single(LinkRelation relation) {
        return new RenderAsLinkRelation(relation, RenderSingleLinks.AS_SINGLE);
    }

    /**
     * Marks a relation as always being rendered as an array of links
     */
    public static RenderAsLinkRelation array(LinkRelation relation) {
        return new RenderAsLinkRelation(relation, RenderSingleLinks.AS_ARRAY);
    }

}
