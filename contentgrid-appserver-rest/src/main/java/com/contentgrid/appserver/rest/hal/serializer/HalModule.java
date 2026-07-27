package com.contentgrid.appserver.rest.hal.serializer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkRelation;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.mediatype.hal.CurieProvider;
import org.springframework.hateoas.mediatype.hal.HalConfiguration.RenderSingleLinks;
import org.springframework.hateoas.mediatype.hal.HalLinkRelation;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdContainerSerializer;

/**
 * Overrides the spring-hateoas HAL module to allow configuring {@link RenderSingleLinks} on a per-link basis by using {@link RenderAsLinkRelation}.
 * <p>
 * When not specified, it keeps the default behavior of rendering as single link when there is only one link and rendering as array when there are multiple.
 * Rendering multiple links that are specified as {@link RenderAsLinkRelation#single(LinkRelation)} is an error
 */
public class HalModule extends SimpleModule {

    public HalModule() {
        super("cg-hal-module");

        setMixInAnnotation(RepresentationModel.class, RepresentationModelMixin.class);
    }

    public static class HalLinksSerializer extends StdContainerSerializer<Links> {
        private final CurieProvider curieProvider;

        @With(value = AccessLevel.PRIVATE)
        private final BeanProperty property;

        @Autowired
        public HalLinksSerializer(
                CurieProvider curieProvider
        ) {
            this(curieProvider, null);
        }

        public HalLinksSerializer(CurieProvider curieProvider, BeanProperty property) {
            super(Links.class);
            this.curieProvider = curieProvider;
            this.property = property;
        }

        @Override
        public JavaType getContentType() {
            return null;
        }

        @Override
        public ValueSerializer<?> getContentSerializer() {
            return null;
        }

        @Override
        public boolean isEmpty(SerializationContext prov, Links value) {
            return value == null || value.isEmpty();
        }

        @Override
        public boolean hasSingleElement(Links value) {
            return value.hasSingleLink();
        }

        @Override
        protected StdContainerSerializer<?> _withValueTypeSerializer(TypeSerializer vts) {
            return null;
        }

        @Override
        public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
            return withProperty(property);
        }

        @Override
        public void serialize(Links value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            var halLinksCollection = new HalLinksCollection();
            value.forEach(halLinksCollection::add);

            boolean isInRoot = gen.streamWriteContext().getParent().inRoot();

            if (isInRoot) {
                curieProvider.getCurieInformation(value)
                        .stream()
                        .map(Link.class::cast)
                        .forEachOrdered(halLinksCollection::add);
            }


            var typeFactory = ctxt.getTypeFactory();
            var mapType = typeFactory.constructMapType(LinkedHashMap.class, String.class, Object.class);

            ctxt.findPrimaryPropertySerializer(mapType, property)
                    .serialize(halLinksCollection.sortedLinks, gen, ctxt);
        }

    }

    private static class HalLinksCollection {
        private static final Map<LinkRelation, MergeLink> OVERRIDES = Map.of(
                HalLinkRelation.CURIES, MergeLink.MERGE_ARRAY // Special case: `curies` is always an array
        );

        private final Map<String, Object> sortedLinks = new LinkedHashMap<>();

        public void add(Link link) {
            var merge = getMergeStrategy(link.getRel());

            sortedLinks.compute(link.getRel().value(), (_key, existingValue) -> merge.merge(existingValue, link));
        }

        private MergeLink getMergeStrategy(LinkRelation relation) {
            return OVERRIDES.entrySet()
                    .stream()
                    .filter(e -> e.getKey().isSameAs(relation))
                    .findFirst()
                    .map(Entry::getValue)
                    .orElseGet(() -> switch(relation) {
                        case RenderAsLinkRelation renderAsLinkRelation when renderAsLinkRelation.getRenderSingleLinks() == RenderSingleLinks.AS_SINGLE -> MergeLink.MERGE_SINGLE;
                        case RenderAsLinkRelation renderAsLinkRelation when renderAsLinkRelation.getRenderSingleLinks() == RenderSingleLinks.AS_ARRAY -> MergeLink.MERGE_ARRAY;
                        default -> MergeLink.MERGE_DEFAULT;
                    });
        }

    }

    @RequiredArgsConstructor
    private enum MergeLink {
        MERGE_DEFAULT((existingValues, newValue) -> {
            if (existingValues.isEmpty()) {
                return newValue;
            }
            existingValues.add(newValue);
            return existingValues;
        }),
        MERGE_SINGLE((existingValues, newValue) -> {
            if (!existingValues.isEmpty()) {
                throw new IllegalStateException("Link relation '%s' is configured to be only single-valued, but there are multiple links present".formatted(newValue.getRel().value()));
            }
            return newValue;
        }),
        MERGE_ARRAY((existingValues, newValue) -> {
            existingValues.add(newValue);
            return existingValues;
        }),
        ;

        @NonNull
        private final BiFunction<List<Link>, Link, Object> mergeFunction;

        public Object merge(Object existingValue, @NonNull Object newValue) {
            if(existingValue instanceof List<?> list) {
                return mergeFunction.apply((List<Link>) list, (Link)newValue);
            }
            var newList = new ArrayList<Link>();
            if (existingValue != null) {
                newList.add((Link) existingValue);
            }
            return mergeFunction.apply(newList, (Link) newValue);
        }

    }


}
