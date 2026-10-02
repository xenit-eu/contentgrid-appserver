package com.contentgrid.appserver.rest.mapping.specialized;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.attributes.Attribute;
import com.contentgrid.appserver.application.model.attributes.ContentAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute;
import com.contentgrid.appserver.application.model.attributes.SimpleAttribute.Type;
import com.contentgrid.appserver.application.model.links.StoredEntityLink;
import com.contentgrid.appserver.application.model.propertypath.PropertyPathResolver;
import com.contentgrid.appserver.application.model.values.PathSegmentName;
import com.contentgrid.appserver.rest.mapping.SpecializedOnLinkType;
import com.contentgrid.appserver.rest.mapping.SpecializedOnLinkType.StoredLinkType;
import com.contentgrid.appserver.rest.mapping.specialized.PathParameterSpecializer.PathParameterPatternsSupplier;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

class SpecializedOnLinkTypeAnnotationSpecializationHandler implements SpecializationHandler<SpecializedOnLinkType> {

    @Override
    public Optional<Specializer> getSpecializerFor(SpecializedOnLinkType annotation) {
        return Optional.of(new PathParameterSpecializer(new PathParameterPatternsSupplier() {
                    @Override
                    public Set<String> getPathParameters() {
                        return Set.of(annotation.entityPathVariable(), annotation.linkPathVariable());
                    }

                    @Override
                    public Stream<Map<String, String>> getPathParameterValues(Application application) {
                        var filter = new LinkTypeFilter(application.getPropertyPathResolver(), attributeFilter(annotation.type()));
                        return application.getEntities().stream()
                                .flatMap(entity ->
                                        filter.resolve(entity)
                                                .map(link -> Map.of(
                                                        annotation.entityPathVariable(), entity.getPathSegment().getValue(),
                                                        annotation.linkPathVariable(), link.getPathSegments().stream()
                                                                .map(PathSegmentName::getValue)
                                                                .collect(Collectors.joining("/"))
                                                ))
                                );
                    }
                })
        );
    }

    private static Predicate<Attribute> attributeFilter(StoredLinkType[] types) {
        Predicate<Attribute> predicate = a -> false;
        for (var type : types) {
            predicate = predicate.or(attributeFilter(type));
        }
        return predicate;
    }

    private static Predicate<Attribute> attributeFilter(StoredLinkType type) {
        return switch (type) {
            case TEXT -> a -> a instanceof SimpleAttribute sa && sa.getType() == Type.TEXT;
            case CONTENT -> a -> a instanceof ContentAttribute;
        };
    }

    @RequiredArgsConstructor
    private static class LinkTypeFilter {
        @NonNull
        private final PropertyPathResolver propertyPathResolver;

        @NonNull
        private final Predicate<Attribute> attributeFilter;

        private Stream<StoredEntityLink> storedLinks(Entity entity) {
            return entity.getLinks().stream()
                    .flatMap(l -> l instanceof StoredEntityLink sl ? Stream.of(sl) : Stream.empty());
        }

        public Stream<StoredEntityLink> resolve(Entity entity) {
            return storedLinks(entity)
                    .filter(link -> attributeFilter.test(propertyPathResolver.resolveAttribute(entity.getName(), link.getStorage()).getAttribute()));
        }
    }
}
