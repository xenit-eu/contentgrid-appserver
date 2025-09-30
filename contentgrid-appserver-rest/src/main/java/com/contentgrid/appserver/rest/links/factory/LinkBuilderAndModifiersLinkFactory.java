package com.contentgrid.appserver.rest.links.factory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.server.LinkBuilder;

@RequiredArgsConstructor
class LinkBuilderAndModifiersLinkFactory implements CustomizableLinkFactory {
    @NonNull
    private final LinkBuilder linkBuilder;

    @With(value = AccessLevel.PRIVATE)
    @NonNull
    private final List<UnaryOperator<Link>> customizations;

    LinkBuilderAndModifiersLinkFactory(@NonNull LinkBuilder linkBuilder) {
        this(linkBuilder, List.of());
    }

    private LinkBuilderAndModifiersLinkFactory withCustomization(UnaryOperator<Link> customization) {
        var copy = new ArrayList<>(customizations);
        copy.add(customization);
        return withCustomizations(copy);
    }

    @Override
    public URI toUri() {
        return linkBuilder.toUri();
    }

    @Override
    public Link withRel(LinkRelation linkRelation) {
        var link = linkBuilder.withRel(linkRelation);

        for (var customization : customizations) {
            link = customization.apply(link);
        }

        return link;
    }

    @Override
    public CustomizableLinkFactory withName(String name) {
        return withCustomization(l -> l.withName(name));
    }

    @Override
    public CustomizableLinkFactory withTitle(String title) {
        return withCustomization(l -> l.withTitle(title));
    }

    @Override
    public CustomizableLinkFactory withType(String type) {
        return withCustomization(l -> l.withType(type));
    }

    @Override
    public CustomizableLinkFactory withProfile(String profile) {
        return withCustomization(l -> l.withProfile(profile));
    }

    @Override
    public CustomizableLinkFactory withDeprecation(String deprecation) {
        return withCustomization(l -> l.withDeprecation(deprecation));
    }

    @Override
    public CustomizableLinkFactory withHreflang(String name) {
        return withCustomization(l -> l.withHreflang(name));
    }
}
