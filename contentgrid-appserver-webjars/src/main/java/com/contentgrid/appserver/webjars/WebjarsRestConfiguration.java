package com.contentgrid.appserver.webjars;

import com.contentgrid.appserver.webjars.swagger.ui.SwaggerUIInitializerController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SwaggerUIInitializerController.class)
public class WebjarsRestConfiguration {

}
