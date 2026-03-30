package com.contentgrid.appserver.autoconfigure.rest;

import com.contentgrid.appserver.rest.ContentGridRestConfiguration;
import com.contentgrid.appserver.rest.EntityRestController;
import com.contentgrid.thunx.predicates.model.ThunkExpression;
import com.contentgrid.thunx.spring.security.AbacContextSupplier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Import;
import org.springframework.hateoas.RepresentationModel;

@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({EntityRestController.class, RepresentationModel.class, AbacContextSupplier.class, ThunkExpression.class})
@Import(ContentGridRestConfiguration.class)
public class ContentGridRestAutoConfiguration {

}
