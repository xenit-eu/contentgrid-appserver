package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModel;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;

public class ContentGridRestFormatterConfiguration {

    @Bean
    public RestEntityFormatter formatter(
            EntityDataRepresentationModelAssembler assembler,
            MethodLinkBuilderFactory<?> linkBuilderFactory,
            HttpMessageConverters httpMessageConverters
    ) {
        var mapper = selectObjectMapperFor(httpMessageConverters, EntityDataRepresentationModel.class)
                    .orElseThrow(() -> new IllegalStateException("No Jackson HttpMessageConverter available"));
        return new RestEntityFormatter(assembler, linkBuilderFactory, mapper);
    }


    private Optional<ObjectMapper> selectObjectMapperFor(HttpMessageConverters httpMessageConverters, Class<?> type) {
        return httpMessageConverters.getConverters().stream()
                .filter(AbstractJackson2HttpMessageConverter.class::isInstance)
                .map(AbstractJackson2HttpMessageConverter.class::cast)
                .filter(converter -> converter.canWrite(type, MediaTypes.HAL_JSON))
                .map(converter -> converter.getObjectMappersForType(type).get(MediaTypes.HAL_JSON))
                .filter(Objects::nonNull)
                .findFirst();
    }


}
