package com.contentgrid.appserver.autoconfigure.rest;

import com.contentgrid.appserver.autoconfigure.events.ContentGridEventsAutoConfiguration;
import com.contentgrid.appserver.rest.ContentGridRestFormatterConfiguration;
import com.contentgrid.appserver.rest.entity.EntityRestController;
import com.contentgrid.appserver.rest.entity.assembler.EntityDataRepresentationModelAssembler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.RepresentationModel;

@AutoConfiguration(before = {ContentGridEventsAutoConfiguration.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({EntityRestController.class, RepresentationModel.class, EntityDataRepresentationModelAssembler.class})
@Import(ContentGridRestFormatterConfiguration.class)
public class ContentGridRestFormatterAutoConfiguration {
}
