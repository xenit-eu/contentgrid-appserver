package com.contentgrid.appserver.webjars;

import com.contentgrid.appserver.webjars.hal.explorer.HalExplorerController;
import com.contentgrid.appserver.webjars.swagger.ui.SwaggerUIInitializerController;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({SwaggerUIInitializerController.class, HalExplorerController.class})
public class WebjarsRestConfiguration {

}
