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
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerMapping;

class SpecializedOnLinkTypeAnnotationSpecializationHandler implements SpecializationHandler<SpecializedOnLinkType> {

    @Override
    public Optional<Specializer> getSpecializerFor(SpecializedOnLinkType annotation) {
        return Optional.of(new MultiSegmentPathParameterSpecializer(new PathParameterPatternsSupplier() {
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
                                                                .map(Pattern::quote)
                                                                // Note: slash needs to be escaped for the spring PathPatternParser
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

    private static class MultiSegmentPathParameterSpecializer extends PathParameterSpecializer {
        private final PathParameterPatternsSupplier pathParameterPatternsSupplier;
        public MultiSegmentPathParameterSpecializer(@NonNull PathParameterPatternsSupplier pathParameterPatternsSupplier) {
            super(pathParameterPatternsSupplier);
            this.pathParameterPatternsSupplier = pathParameterPatternsSupplier;;
        }

        @Override
        protected String restrictPathParam(String pathPattern, String paramName, String paramValue) {
            var parts = paramValue.split("/");
            if (parts.length == 1) {
                return super.restrictPathParam(pathPattern, paramName, paramValue);
            }
            return pathPattern.replace(
                    "{%s}".formatted(paramName),
                    createSegments(paramName, parts)
            );
        }

        private String createSegments(String paramName, String[] parts) {
            return IntStream.range(0, parts.length)
                    .mapToObj(i -> "{%s:%s}".formatted(createSegmentParamName(paramName, i), parts[i]))
                    .collect(Collectors.joining("/"));
        }

        private static String createSegmentParamName(String baseName, int i) {
            return "_%s__%d".formatted(baseName, i);
        }

        @Override
        public void handleMatch(Application application, HttpServletRequest request) {
            var uriParams = new HashMap<>((Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE));
            for (var pathParameter : pathParameterPatternsSupplier.getPathParameters()) {
                if (uriParams.containsKey(pathParameter)) {
                    // Contains the exact parameter; we don't have to modify anything and can continue
                    continue;
                }
                var parts = new ArrayList<String>();
                for(int i = 0; ; i++) {
                    var segmentParam = createSegmentParamName(pathParameter, i);
                    if (!uriParams.containsKey(segmentParam)) {
                        // This segment does not exist. Stop gathering segments
                        break;
                    }
                    parts.add(uriParams.remove(segmentParam));
                }
                uriParams.put(pathParameter, String.join("/", parts));
            }

            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Collections.unmodifiableMap(uriParams));
        }
    }
}
