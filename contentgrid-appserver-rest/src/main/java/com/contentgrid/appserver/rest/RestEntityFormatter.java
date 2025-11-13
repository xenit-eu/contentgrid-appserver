package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.i18n.UserLocales;
import com.contentgrid.appserver.domain.data.EntityInstance;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.links.factory.LinkFactoryProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;

@RequiredArgsConstructor
public class RestEntityFormatter {
    private final EntityDataRepresentationModelAssembler assembler;
    private final MethodLinkBuilderFactory<?> linkBuilderFactory;
    private final ObjectMapper mapper;

    public JsonNode format(Application application, EntityInstance entityInstance) {
        var locales = new DummyLocales();
        var linkFactoryProvider = new LinkFactoryProvider(application, locales, linkBuilderFactory);
        var name = entityInstance.getIdentity().getEntityName();
        var model = assembler.withContext(application, name, locales, linkFactoryProvider).toModel(entityInstance);

        return mapper.valueToTree(model);
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
