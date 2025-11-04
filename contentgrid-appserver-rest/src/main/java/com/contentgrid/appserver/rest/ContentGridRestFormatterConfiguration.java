package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.events.EntityFormatter;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;

public class ContentGridRestFormatterConfiguration {

    @Bean
    public EntityFormatter formatter(
            EntityDataRepresentationModelAssembler assembler,
            MethodLinkBuilderFactory<?> linkBuilderFactory,
            HttpMessageConverters httpMessageConverters
    ) {
        return new RestEntityFormatter(assembler, linkBuilderFactory, httpMessageConverters);
    }


    @RequiredArgsConstructor
    private static class RestEntityFormatter implements EntityFormatter {
        private final EntityDataRepresentationModelAssembler assembler;
        private final MethodLinkBuilderFactory<?> linkBuilderFactory;
        private final HttpMessageConverters httpMessageConverters;

        @Override
        public JsonNode format(Application application, EntityInstance entityInstance) {
            var locales = new DummyLocales();
            var linkFactoryProvider = new LinkFactoryProvider(application, locales, linkBuilderFactory);
            var name = entityInstance.getIdentity().getEntityName();
            var model = assembler.withContext(application, name, locales, linkFactoryProvider).toModel(entityInstance);

            // TODO: Should we
            //  - fall back to a default ObjectMapper if we can't find one?
            //  - cache this mapper for other models?
            var mapper = selectObjectMapperFor(model.getClass())
                    .orElseThrow(() -> new IllegalStateException("No Jackson HttpMessageConverter available"));

            return mapper.valueToTree(model);
        }

        private Optional<ObjectMapper> selectObjectMapperFor(Class<?> type) {
            return httpMessageConverters.getConverters().stream()
                    .filter(AbstractJackson2HttpMessageConverter.class::isInstance)
                    .map(AbstractJackson2HttpMessageConverter.class::cast)
                    .filter(converter -> converter.canWrite(type, MediaTypes.HAL_JSON))
                    .map(converter -> converter.getObjectMappersForType(type).get(MediaTypes.HAL_JSON))
                    .filter(Objects::nonNull)
                    .findFirst();
        }

    }

    private static class DummyLocales implements UserLocales {

        @Override
        public Locale resolvePreferredLocale(Collection<Locale> supportedLocales) {
            return Locale.ENGLISH;
        }

        @Override
        public Stream<Locale> preferredLocales() {
            return Stream.of(Locale.ENGLISH);
        }
    }
}
