package com.contentgrid.appserver.webjars;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.webjars.hal.explorer.HalExplorerController;
import com.contentgrid.appserver.webjars.swagger.ui.OpenApiController;
import com.contentgrid.appserver.webjars.swagger.ui.SwaggerUIInitializerController;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({SwaggerUIInitializerController.class, HalExplorerController.class})
public class WebjarsRestConfiguration {

    @Bean
    @SneakyThrows
    OpenApiController openApiController(Artifact artifact) {
        return new OpenApiController(artifact);
    }

}
