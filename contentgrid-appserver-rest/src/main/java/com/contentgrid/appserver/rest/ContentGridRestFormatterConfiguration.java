package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModel;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;

public class ContentGridRestFormatterConfiguration {

    @Bean
    public RestEntityFormatter formatter(
            EntityDataRepresentationModelAssembler assembler,
            MethodLinkBuilderFactory<?> linkBuilderFactory,
            ObjectMapper objectMapper
    ) {
        return new RestEntityFormatter(assembler, linkBuilderFactory, objectMapper);
    }
}
