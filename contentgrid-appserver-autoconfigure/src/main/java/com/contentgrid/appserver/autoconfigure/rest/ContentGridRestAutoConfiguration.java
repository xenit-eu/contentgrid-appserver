package com.contentgrid.appserver.autoconfigure.rest;

import com.contentgrid.appserver.autoconfigure.domain.ContentGridDomainAutoConfiguration;
import com.contentgrid.appserver.domain.ContentApi;
import com.contentgrid.appserver.domain.DatamodelApi;
import com.contentgrid.appserver.rest.ContentGridRestConfiguration;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.thunx.api.autoconfigure.AbacContextAutoConfiguration;
import com.contentgrid.thunx.api.autoconfigure.JwtAbacAutoConfiguration;
import com.contentgrid.thunx.spring.data.context.AbacContextSupplier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.ConversionService;
import org.springframework.hateoas.RepresentationModel;

@AutoConfiguration(after = {ContentGridDomainAutoConfiguration.class, AbacContextAutoConfiguration.class,
        JwtAbacAutoConfiguration.class, WebMvcAutoConfiguration.class})
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({EntityRestController.class, RepresentationModel.class})
@ConditionalOnBean({DatamodelApi.class, ContentApi.class, AbacContextSupplier.class, ConversionService.class})
@Import(ContentGridRestConfiguration.class)
public class ContentGridRestAutoConfiguration {

}
