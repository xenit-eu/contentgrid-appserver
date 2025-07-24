package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.application.model.Application;
import com.contentgrid.appserver.application.model.Entity;
import com.contentgrid.appserver.application.model.values.EntityName;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.With;
import org.springframework.hateoas.AffordanceModel.InputPayloadMetadata;
import org.springframework.hateoas.AffordanceModel.Named;
import org.springframework.hateoas.AffordanceModel.PayloadMetadata;
import org.springframework.hateoas.AffordanceModel.PropertyMetadata;
import org.springframework.hateoas.mediatype.html.HtmlInputType;
import org.springframework.http.MediaType;

@RequiredArgsConstructor
public class HalFormsPayloadMetadataConverter {

    private final Collection<HalFormsPayloadMetadataContributor> contributors;

    public PayloadMetadata convertToCreatePayloadMetadata(Application application, Entity entity) {
        List<PropertyMetadata> properties = new ArrayList<>();

        contributors.stream()
                .flatMap(contributor -> contributor.contributeToCreateForm(application, entity))
                .forEachOrdered(properties::add);

        var hasFiles = properties.stream().anyMatch(prop -> Objects.equals(HtmlInputType.FILE_VALUE, prop.getInputType()));

        return new EntityPayloadMetadata(entity.getName(), properties)
                .withMediaTypes(List.of(hasFiles?MediaType.MULTIPART_FORM_DATA:MediaType.APPLICATION_JSON));
    }

    public PayloadMetadata convertToUpdatePayloadMetadata(Application application, Entity entity) {
        List<PropertyMetadata> properties = new ArrayList<>();

        contributors.stream()
                .flatMap(contributor -> contributor.contributeToUpdateForm(application, entity))
                .forEachOrdered(properties::add);

        return new EntityPayloadMetadata(entity.getName(), properties)
                .withMediaTypes(List.of(MediaType.APPLICATION_JSON));
    }

    public PayloadMetadata convertToSearchPayloadMetadata(Application application, Entity entity) {
        List<PropertyMetadata> properties = new ArrayList<>();

        contributors.stream()
                .flatMap(contributor -> contributor.contributeToSearchForm(application, entity))
                .forEachOrdered(properties::add);

        return new EntityPayloadMetadata(entity.getName(), properties);
    }


    @AllArgsConstructor
    @RequiredArgsConstructor
    private static class EntityPayloadMetadata implements InputPayloadMetadata {
        private final EntityName entityName;
        private final Collection<PropertyMetadata> properties;
        @With
        private List<MediaType> mediaTypes = Collections.emptyList();

        @Override
        public <T extends Named> T customize(T target, Function<PropertyMetadata, T> customizer) {
            return properties.stream()
                    .filter(propMeta -> propMeta.getName().equals(target.getName()))
                    .findAny()
                    .map(customizer)
                    .orElse(target);
        }

        @Override
        public List<String> getI18nCodes() {
            return List.of(entityName.getValue());
        }

        @Override
        public List<MediaType> getMediaTypes() {
            return this.mediaTypes;
        }

        @Override
        public Stream<PropertyMetadata> stream() {
            return properties.stream();
        }

        @Override
        public Class<?> getType() {
            return Object.class;
        }
    }

}
