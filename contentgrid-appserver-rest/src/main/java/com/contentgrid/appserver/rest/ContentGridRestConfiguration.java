package com.contentgrid.appserver.rest;

import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.hal.forms.ContentGridHalFormsConfiguration;
import com.contentgrid.appserver.rest.hal.forms.HalFormsPayloadMetadataConverter;
import com.contentgrid.appserver.rest.links.ContentGridLinksConfiguration;
import com.contentgrid.appserver.rest.problem.ContentgridProblemDetailConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({ContentgridProblemDetailConfiguration.class, ArgumentResolverConfigurer.class, ContentGridLinksConfiguration.class,
        ContentGridHalFormsConfiguration.class})
public class ContentGridRestConfiguration {

    @Bean
    EntityDataRepresentationModelAssembler entityDataRepresentationModelAssembler(HalFormsPayloadMetadataConverter payloadMetadataConverter) {
        return new EntityDataRepresentationModelAssembler(payloadMetadataConverter);
    }

}
