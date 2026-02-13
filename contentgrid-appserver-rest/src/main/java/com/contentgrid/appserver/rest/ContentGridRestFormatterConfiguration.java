package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModel;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;

public class ContentGridRestFormatterConfiguration {

    @Bean
    public RestEntityFormatter formatter(
            EntityDataRepresentationModelAssembler assembler,
            MethodLinkBuilderFactory<?> linkBuilderFactory,
            ObjectMapper objectMapper
    ) {
        var mapper = selectObjectMapperFor(objectMapper, EntityDataRepresentationModel.class)
                    .orElseThrow(() -> new IllegalStateException("No Jackson ObjectMapper available"));
        return new RestEntityFormatter(assembler, linkBuilderFactory, mapper);
    }


    private Optional<ObjectMapper> selectObjectMapperFor(ObjectMapper objectMapper, Class<?> type) {
        return Optional.of(objectMapper);
    }


}
