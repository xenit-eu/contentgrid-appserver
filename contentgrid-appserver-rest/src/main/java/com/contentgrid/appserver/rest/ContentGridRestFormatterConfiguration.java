package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.converter.RequestInputDataJacksonModule;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.hateoas.mediatype.hal.HalMediaTypeConfiguration;
import org.springframework.hateoas.server.MethodLinkBuilderFactory;
import tools.jackson.databind.json.JsonMapper;

public class ContentGridRestFormatterConfiguration {

    @Bean
    public RestEntityFormatter formatter(
            EntityDataRepresentationModelAssembler assembler,
            MethodLinkBuilderFactory<?> linkBuilderFactory,
            HalMediaTypeConfiguration halConfiguration,
            ObjectProvider<JsonMapper> baseMapper,
            RequestInputDataJacksonModule requestInputDataModule
    ) {
        var mapper = baseMapper.getIfAvailable(JsonMapper::new);
        var halMapper = halConfiguration.configureJsonMapper(mapper.rebuild().addModule(requestInputDataModule)).build();
        return new RestEntityFormatter(assembler, linkBuilderFactory, halMapper);
    }

}
