package com.contentgrid.appserver.rest.hal.forms;

import com.contentgrid.appserver.rest.assembler.JsonViews.DefaultView;
import com.contentgrid.appserver.rest.assembler.JsonViews.HalFormsView;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.config.HypermediaMappingInformation;
import org.springframework.hateoas.mediatype.hal.HalMediaTypeConfiguration;
import org.springframework.http.MediaType;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HalFormsMediaTypeConfiguration implements HypermediaMappingInformation {

    @Autowired
    private final HalMediaTypeConfiguration halMediaTypeConfiguration;

    @Override
    public List<MediaType> getMediaTypes() {
        return Collections.singletonList(MediaTypes.HAL_FORMS_JSON);
    }

    @Override
    public ObjectMapper configureObjectMapper(ObjectMapper mapper) {
        mapper = halMediaTypeConfiguration.configureObjectMapper(mapper);
        addView(mapper, HalFormsView.class);

        return mapper;
    }

    @Bean
    public HalFormsPropertyContributor halFormsPropertyContributor() {
        return new HalFormsPropertyContributor();
    }

    @Bean
    public HalFormsTemplateGenerator halFormsTemplateGenerator(HalFormsPropertyContributor halFormsPropertyContributor) {
        return new HalFormsTemplateGenerator(halFormsPropertyContributor);
    }

    /**
     * Bean that customizes the {@link ObjectMapper} to only expose properties with the {@link DefaultView}.
     * Properties with a different view will be ignored.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer defaultViewObjectMapperCustomizer() {
        return builder -> builder.postConfigurer(objectMapper -> addView(objectMapper, DefaultView.class));
    }

    private void addView(ObjectMapper objectMapper, Class<?> view) {
        var serializationConfig = objectMapper.getSerializationConfig();
        serializationConfig = serializationConfig.withView(view);
        serializationConfig = serializationConfig.with(MapperFeature.DEFAULT_VIEW_INCLUSION);
        objectMapper.setConfig(serializationConfig);
    }
}
