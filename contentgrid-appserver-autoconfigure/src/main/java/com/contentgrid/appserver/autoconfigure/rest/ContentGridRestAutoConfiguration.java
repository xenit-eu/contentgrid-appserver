package com.contentgrid.appserver.autoconfigure.rest;

import com.contentgrid.appserver.autoconfigure.domain.ContentGridDomainAutoConfiguration;
import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.registry.DefaultApplicationNameExtractor;
import com.contentgrid.appserver.rest.ContentGridRestConfiguration;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.appserver.rest.ProfileRestController;
import com.contentgrid.appserver.rest.RootRestController;
import com.contentgrid.appserver.rest.assembler.EntityDataRepresentationModelAssembler;
import com.contentgrid.appserver.rest.assembler.profile.hal.ProfileEntityRepresentationModelAssembler;
import com.contentgrid.appserver.rest.converter.RequestInputDataJacksonModule;
import com.contentgrid.appserver.rest.converter.UriListHttpMessageConverter;
import com.contentgrid.appserver.rest.filter.SingleRangeRequestServletFilter;
import com.contentgrid.appserver.rest.mapping.ContentGridHandlerMappingConfiguration;
import com.contentgrid.appserver.rest.property.ContentRestController;
import com.contentgrid.appserver.rest.property.XToManyRelationRestController;
import com.contentgrid.appserver.rest.property.XToOneRelationRestController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.RepresentationModel;

@AutoConfiguration(after = ContentGridDomainAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({EntityRestController.class, RepresentationModel.class})
@ConditionalOnBean({DatamodelApi.class, ContentApi.class})
@Import({
        ContentGridHandlerMappingConfiguration.class,
        ContentGridRestConfiguration.class,
        ContentRestController.class,
        DefaultApplicationNameExtractor.class,
        EntityDataRepresentationModelAssembler.class,
        EntityRestController.class,
        ProfileEntityRepresentationModelAssembler.class,
        ProfileRestController.class,
        RequestInputDataJacksonModule.class,
        RootRestController.class,
        SingleRangeRequestServletFilter.class,
        UriListHttpMessageConverter.class,
        XToManyRelationRestController.class,
        XToOneRelationRestController.class,
})
public class ContentGridRestAutoConfiguration {

}
