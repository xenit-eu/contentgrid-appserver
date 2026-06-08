package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModel;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.config.WebConverters;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

public class ContentGridRestFormatterConfiguration {

    @Bean
    public RestEntityFormatter formatter(
            EntityDataRepresentationModelAssembler assembler,
            MethodLinkBuilderFactory<?> linkBuilderFactory,
            WebConverters webConverters
    ) {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        webConverters.augmentServer(converters);
        var mapper = selectObjectMapperFor(converters, EntityDataRepresentationModel.class)
                    .orElseThrow(() -> new IllegalStateException("No Jackson HttpMessageConverter available"));
        return new RestEntityFormatter(assembler, linkBuilderFactory, mapper);
    }


    private Optional<JsonMapper> selectObjectMapperFor(List<HttpMessageConverter<?>> converters, Class<?> type) {
        return converters.stream()
                .filter(AbstractJacksonHttpMessageConverter.class::isInstance)
                .map(AbstractJacksonHttpMessageConverter.class::cast)
                .filter(converter -> converter.canWrite(type, MediaTypes.HAL_JSON))
                .map(AbstractJacksonHttpMessageConverter::getMapper)
                .map(JsonMapper.class::cast)
                .findFirst();
    }


}
