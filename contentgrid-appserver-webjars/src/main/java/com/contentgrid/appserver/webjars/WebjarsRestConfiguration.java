package com.contentgrid.appserver.webjars;

import com.contentgrid.appserver.infrastructure.api.Artifact;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntry;
import com.contentgrid.appserver.infrastructure.api.ArtifactEntryNotFoundException;
import com.contentgrid.appserver.webjars.hal.explorer.HalExplorerController;
import com.contentgrid.appserver.webjars.swagger.ui.OpenApiController;
import com.contentgrid.appserver.webjars.swagger.ui.SwaggerUIInitializerController;
import java.nio.file.Path;
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
        ArtifactEntry entry;
        try {
            entry = artifact.load(Path.of("META-INF", "resources", "openapi.yml"));
        } catch (ArtifactEntryNotFoundException e) {
            entry = null;
        }
        return new OpenApiController(entry);
    }

}
