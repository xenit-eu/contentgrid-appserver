package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.application.model.values.EntityName;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.domain.events.EntityFormatter;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;

public class ContentGridRestFormatterConfiguration {
    @Bean
    public EntityFormatter formatter(
            EntityDataRepresentationModelAssembler assembler,
            MethodLinkBuilderFactory<?> linkBuilderFactory
    ) {
        return new RestEntityFormatter(assembler, linkBuilderFactory);
    }


    @RequiredArgsConstructor
    private static class RestEntityFormatter implements EntityFormatter {
        private final EntityDataRepresentationModelAssembler assembler;
        private final MethodLinkBuilderFactory<?> linkBuilderFactory;
        private final ObjectMapper mapper = JsonMapper.builder()
                .configure(MapperFeature.REQUIRE_HANDLERS_FOR_JAVA8_TIMES, false)
                .build();

        @Override
        public JsonNode format(Application application, EntityInstance entityInstance) {
            var locales = new DummyLocales();
            var linkFactoryProvider = new LinkFactoryProvider(application, locales, linkBuilderFactory);
            var name = entityInstance.getIdentity().getEntityName();
            var model = assembler.withContext(application, name, locales, linkFactoryProvider).toModel(entityInstance);
            return mapper.valueToTree(model);
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
